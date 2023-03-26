package com.hhsdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hhsdp.service.impl.ShopServiceImpl.CACHE_REBUILD_EXECUTOR;
import static com.hhsdp.utils.SystemConstants.*;


@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //设置缓存工具
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    //设置逻辑过期时间
    public void setWithLogical(String key, Object value, Long time, TimeUnit unit){

        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    //设置缓存穿透
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID,R> dbFallBack,Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否命中
        if(StrUtil.isNotBlank(json)){
            //命中有效数据直接返回
            return JSONUtil.toBean(json, type);
        }

        //判断命中的是否是空值
        if(json != null){
            return null;
        }

        //3.未命中，查询数据库
        R r = dbFallBack.apply(id);

        //4.查询是否存在
        if (r == null){
            //数据为空也缓存到redis中，防止缓存穿透
            stringRedisTemplate.opsForValue().set("key","",CACHE_NULL_TTL,TimeUnit.SECONDS);
            return null;
        }

        //5.将查询数据写会到cache中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),time,unit);

        return r;
    }


    //逻辑过期防止缓存击穿
    public  <R,ID> R queryWithLogicExpire(String keyPrefix, ID id,Class<R> type,
                                          Function<ID,R> dbFallBack, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否命中
        if(StrUtil.isBlank(json)){
            //未命中有效数据直接返回
            return null;
        }

        //序列化对象取出数据判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //未过期，返回数据
            return r;
        }
        //过期，获取互斥锁
        String lockKey = "lock:shop:" + key;
        boolean isLock = tryLock(lockKey);

        //判断是否获取成功
        if(isLock){
            //获取成功，开启独立线程查询数据库并写回缓存
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //重建缓存
                try {
                    //查询缓存
                    R r1 = dbFallBack.apply(id);
                    //写入缓存
                    setWithLogical(key,id,time,unit);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    UnLock(lockKey);
                }
            });
        }

        return r;
    }

    //启用互斥锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "remedios", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放互斥锁
    private void UnLock(String key){
        Boolean flag = stringRedisTemplate.delete(key);
    }


}
