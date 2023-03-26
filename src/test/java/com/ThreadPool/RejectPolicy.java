package com.ThreadPool;

import jdk.nashorn.internal.objects.annotations.Function;

/**
 * @author: remedios
 * @Description:
 * @create: 2022-11-12 12:05
 */

@FunctionalInterface
public interface RejectPolicy<T>{
    void reject(BlockingQueue<T> queue, T task);
}
