package com.hhsdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhsdp.dto.Result;
import com.hhsdp.entity.Shop;
import com.hhsdp.mapper.ShopMapper;
import com.hhsdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhsdp.utils.CacheClient;
import com.hhsdp.utils.RedisData;
import com.hhsdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hhsdp.utils.RedisConstants.SHOP_GEO_KEY;
import static com.hhsdp.utils.SystemConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool( 10);


    @Override
    public Result QueryById(Long id) {

        //解决缓存穿透
        //Shop shop = QueryWithPassThrough(id);
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //利用互斥锁解决缓存击穿问题
        Shop shop = queryWithMutex(id);

        //通过设置逻辑过期时间防止缓存击穿,店铺信息需要自行设置到redis中
        //Shop shop = queryWithLogicExpire(id);
        //Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,10L,TimeUnit.SECONDS);

        if(shop == null){
            return Result.fail("店铺信息不存在");
        }

        return Result.ok(shop);

    }

    /**
     * 设置返回空值redis防止缓存穿透
     * @param id
     * @return
     */
    private Shop QueryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否命中
        if(StrUtil.isNotBlank(cacheShop)){
            //命中有效数据直接返回
            Shop shop = JSONUtil.toBean(cacheShop, Shop.class);
            return shop;
        }

        //判断命中的是否是空值
        if(cacheShop != null){
            return null;
        }

        //3.未命中，查询数据库
        Shop shop = getById(id);

        //4.查询是否存在
        if (shop == null){
            //数据为空也缓存到redis中，防止缓存穿透
            stringRedisTemplate.opsForValue().set("key","",CACHE_NULL_TTL,TimeUnit.SECONDS);
            return null;
        }

        //5.将查询数据写会到cache中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);

        return shop;
    }


    /**
     * 通过设置互斥锁防止缓存击穿
     * 互斥锁防止查询缓存为空后，多个线程尝试查询数据库并更新缓存
     */
    private Shop queryWithMutex(Long id){

        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否命中
        if(StrUtil.isNotBlank(cacheShop)){
            //命中有效数据直接返回
            return JSONUtil.toBean(cacheShop, Shop.class);
        }

        //判断命中的是否是空值(因为上面已经判断过有数据的直接返回，
        // 这里只剩下两种情况1.为null  2.为空字符串"  ")
        if(cacheShop != null){
            return null;
        }

        Shop shop = null;

        String lockKey = "lock:shop:" + key;

        try {
            //获取互斥锁
            boolean tryLock = tryLock(lockKey);
            //判断是否获取成功
            if (!tryLock){
                //休眠
                Thread.sleep(50);
            }

            //3.未命中，查询数据库
            shop = getById(id);

            //4.查询是否存在
            if (shop == null){
                //数据为空也缓存到redis中，防止缓存穿透
                stringRedisTemplate.opsForValue().set("key","",CACHE_NULL_TTL,TimeUnit.SECONDS);
                return null;
            }

            //5.将查询数据写会到cache中
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            UnLock(lockKey);
        }

        return shop;
    }




    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要进行分类查询
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //查询redis
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );

        //解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }

        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());

        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        //此处为了是按照id顺序显示，用list数据去数据库查询后排名顺序会改变
        //需添加orderby语句并手动设置数据
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            //为shop设置地址值
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);

    }

    /**
     * 将shop对象转换封装一个过期时间，类RedisData
     * @param id
     * @param expireTime
     */
    public void saveShopToRedis(Long id,Long expireTime){

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Shop shop = getById(id);
        RedisData data = new RedisData();
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        data.setData(shop);

        //写入到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop.toString()));

    }

    @Override
    @Transactional
    public Result update(Shop shop) {

        if(shop.getId() == null){
            return Result.fail("店铺id不能为空！");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }



    private boolean tryLock(String key){

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "remedios", 10, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(flag);
    }

    private void UnLock(String key){

        Boolean flag = stringRedisTemplate.delete(key);
    }
}
