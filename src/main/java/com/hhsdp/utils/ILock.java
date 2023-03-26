package com.hhsdp.utils;

/**
 * @author: remedios
 * @Description:
 * @create: 2022-10-13 18:40
 */
public interface ILock {

    boolean tryLock(Long timeoutSec);

    void unlock();
}
