package com.hhsdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hhsdp.dto.Result;
import com.hhsdp.entity.VoucherOrder;
import com.hhsdp.mapper.VoucherOrderMapper;
import com.hhsdp.service.ISeckillVoucherService;
import com.hhsdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhsdp.utils.RedisIdWorker;
import com.hhsdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("secKill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //创建线程池处理队列信息
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //使类加载完成后就开始执行VoucherOrderHandler这个任务
    @PostConstruct
    private void init(){
      //SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while(true){
                try {
                    //1.获取消息队列中的订单信息 XREADGROUP g1(组名) c1(消费者名)
                                            // COUNT 1(每次取出的消息数量） BLOCK 2000(消息的等待时间)
                                            // STREAMS streams.order(消息队列的名字) >(取值的规则)
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed()));
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()){
                        //如果为空说明没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> recordValue = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(recordValue, new VoucherOrder(), true);
                    //4.获取成功，就可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.info("处理订单异常{}",e);
                    handlePendingList();
                }
            }
        }


        private void handlePendingList() {
            while(true){
                try {
                    //1.获取pendingList中的订单信息 XREADGROUP g1(组名) c1(消费者名)
                    // COUNT 1(每次取出的消息数量) STREAMS streams.order(消息队列的名字) 0(取值的规则)
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0")));

                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()){
                        //如果为空说明pendingList中没有消息，可以结束循环。
                        break;
                    }
                    //3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> recordValue = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(recordValue, new VoucherOrder(), true);
                    //4.获取成功，就可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.info("处理订单异常{}",e);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }

        }

    }

    //使用阻塞队列完成异步秒杀
    private BlockingQueue<VoucherOrder> orderTask= new ArrayBlockingQueue<>(1024*1024);
    //VoucherOrderHandler不断的从阻塞队列中读取信息，为空时进入阻塞状态
    private class VoucherOrderHandler1 implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    VoucherOrder voucherOrder = orderTask.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.info("处理订单异常{}",e);
                }
            }
        }

    }


    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //创建锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock){
            log.info("同一用户不能重复下单！");
            return;
        }

        try{
            //获取代理对象（事务）
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result secKillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //创建订单id
        long orderId = redisIdWorker.nextId("order");
        //执行lua脚本
        int result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()).intValue();
                String.valueOf(orderId);

        //判断结果是否为0
        if(result != 0){
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);

    }

//    @Override
//    public Result secKillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        //执行lua脚本
//        int result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString()).intValue();
//
//
//        //判断结果是否为0
//        if(result != 0){
//            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
//        }
//
//        //有购买资格,将订单信息添加到阻塞队列中去
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //设置订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //设置用户id
//        voucherOrder.setUserId(userId);
//        //设置优惠券id
//        voucherOrder.setVoucherId(voucherId);
//        //添加到阻塞队列中
//        orderTask.add(voucherOrder);
//
//        //获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //返回订单id
//        return Result.ok(orderId);
//    }

//    @Override
//    public Result secKillVoucher(Long voucherId) {
//
//        //查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        //判断秒杀是否开始
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            //返回异常结果
//            return Result.fail("秒杀活动尚未开始！");
//        }
//        //判断秒杀是否结束
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            //返回异常结果
//            return Result.fail("秒杀活动已结束！");
//        }
//
//        //判断库存是否充足
//        if(voucher.getStock() < 1){
//            //返回异常结果
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//        //synchronized (userId.toString().intern()) {}
//        //SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //创建锁
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        if (!isLock){
//            return Result.fail("同一用户只能下一单");
//        }
//
//        try{
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//
//    }


    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        //一人一单
        Long userId = voucherOrder.getUserId();

        Integer count = query().eq("user_id", userId)
                                .eq("voucher_id", voucherOrder).count();
        if (count > 0){
            log.info("用户已经购买过了！");
            return;
        }

        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder)
                .gt("stock",0)
                .update();
        if(!success){
            log.info("库存不足");
            return;
        }

        save(voucherOrder);
    }


}