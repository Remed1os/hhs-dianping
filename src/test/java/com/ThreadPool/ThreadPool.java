package com.ThreadPool;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * @author: remedios
 * @Description:
 * @create: 2022-11-12 12:04
 */

@Slf4j(topic = "c.ThreadPool")
public class ThreadPool {

    //任务队列
    private BlockingQueue<Runnable> taskQueue;

    //线程集合
    private HashSet<Worker> workers = new HashSet<>();

    //核心线程数
    private int coreSize;

    //拒绝策略
    private RejectPolicy<Runnable> rejectPolicy;

    //获取任务时间的超时时间
    private long timeout;
    private TimeUnit timeUnit;


    //构造器
    public ThreadPool(int coreSize, long timeout, TimeUnit timeUnit, int queueCapcity, RejectPolicy<Runnable> rejectPolicy) {
        log.info("构造ThreadPool");
        this.coreSize = coreSize;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.taskQueue = new BlockingQueue<>(queueCapcity);
        this.rejectPolicy = rejectPolicy;
    }

    //执行任务
    public void execute(Runnable task){

    }


    //工作线程
    class Worker extends Thread{

        private Runnable task;

        public Worker(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            log.info("跑起来了,让我看看有没有task来做");
        // 执行任务
        // 1) 当 task 不为空，执行任务
        // 2) 当 task 执行完毕，再接着从任务队列获取任务并执行
//            while(task != null || (task = taskQueue.take()) != null) {
            while(task != null || (task = taskQueue.poll(timeout, timeUnit)) != null) {
                try {
                    log.debug("获取到任务了,正在执行...{}", task);
                    task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    log.info("搞定一个任务 {},尝试获取新任务执行",task);
                    task = null;
                 }
            }
            synchronized (workers) {
                log.debug("worker 因长时间没有可执行任务 将被释放 {}", this);
                workers.remove(this);
            }
        }

    }


}


