package com.ThreadPool;

import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author: remedios
 * @Description:
 * @create: 2022-11-12 12:04
 */


@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j(topic = "c.BlockingQueue")
class BlockingQueue<T>{

    //1.先创建任务队列
    private Deque<T> queue = new ArrayDeque<>();

    //2.创建锁对象
    private ReentrantLock lock = new ReentrantLock();

    //3.生产者变量
    private Condition fullWaitSet = lock.newCondition();

    //4.消费者变量
    private Condition emptyWaitSet = lock.newCondition();

    //5.容量
    private int capacity;

    public BlockingQueue() {
    }

    //线程构造器
    public BlockingQueue(int capacity) {
        log.info("构造BlockingQueue");
        this.capacity = capacity;
    }

    //阻塞获取
    public T take(){
        lock.lock();
        try {
           while(queue.isEmpty()){
               try{
                   emptyWaitSet.await();
               }catch (InterruptedException e) {
                   e.printStackTrace();
               }
           }
           T t = queue.removeFirst();
           fullWaitSet.signal();
           return t;
        }finally {
            lock.unlock();
        }
    }

    //阻塞添加
    public void put(T task){
        lock.lock();
        try {
            while(queue.size() == capacity){
                try{
                    fullWaitSet.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            queue.add(task);
            emptyWaitSet.signal();
        }finally {
            lock.unlock();
        }
    }

    //超时获取
    public T poll(long timeout,TimeUnit timeUnit){
        lock.lock();
        try {
            long nano = timeUnit.toNanos(timeout);
            while(queue.isEmpty()){
                try{
                    if(nano <= 0){
                        return null;
                    }
                    nano = emptyWaitSet.awaitNanos(nano);
                }catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            T t = queue.removeFirst();
            fullWaitSet.signal();
            return t;
        }finally {
            lock.unlock();
        }
    };

    //超时添加
    public boolean offer(T task,long timeout,TimeUnit timeUnit){
        lock.lock();
        long nanos = timeUnit.toNanos(timeout);
        try {
            while(queue.size() == capacity){
                try{
                    if(nanos <= 0){
                        return false;
                    }
                    log.debug("等待加入任务队列 {} ...", task);
                    nanos = fullWaitSet.awaitNanos(nanos);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.debug("加入任务队列 {}", task);
            queue.add(task);
            emptyWaitSet.signal();
            return true;
        }finally {
            lock.unlock();
        }
    }





}
