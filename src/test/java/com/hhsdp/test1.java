package com.hhsdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


/**
 * @author: remedios
 * @Description:
 * @create: 2022-10-20 8:39
 */

@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class test1 {

    public static void main(String[] args){

        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.1.100:6379");

        RedissonClient redissonClient = Redisson.create(config);



    }

    public void test1(){
        int i = 2;
    }

    public void test2(){
        int i = 2;
        Integer integer = Integer.valueOf("123");
        System.out.println(integer);
    }

    static class love {

        public void love() {
            for (float y=1.5f;y>-1.5f;y-=0.15f){
                for (float x=-1.5f;x<1.5f;x+=0.05){
                    float a = x*x+y*y-1;
                    if ((a*a*a-x*x*y*y*y)<=0.0){
                        System.out.print("*");
                    }else {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            }
            System.out.println("                        木炉星");
        }
    }


}

