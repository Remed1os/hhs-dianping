package com.hhsdp.config;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: remedios
 * @Description:
 * @create: 2022-10-13 20:22
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){

        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.1.100:6379");

        return Redisson.create(config);
    }
}
