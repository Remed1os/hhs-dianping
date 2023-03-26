package com.hhsdp;

import com.hhsdp.entity.Shop;
import com.hhsdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hhsdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    void test1(){
        shopService.saveShopToRedis(1L,10L);
    }

    @Test
    void test2(){
        List<Shop> shopList = shopService.list();
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //获取店铺类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //获取所有店铺数值
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
//              stringRedisTemplate.opsForGeo().
                      locations.add(new RedisGeoCommands.GeoLocation<>(
                                shop.getId().toString(),
                                new Point(shop.getX(), shop.getY())
                      )
                );
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
