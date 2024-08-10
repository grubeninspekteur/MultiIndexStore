package com.github.multiindexstore.internal;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class ReadWriteGuard {

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public <T> T readGuard(Supplier<T> supplier) {
    try {
      lock.readLock().lock();
      return supplier.get();
    } finally {
      lock.readLock().unlock();
    }
  }

  public <T> T writeGuard(Supplier<T> supplier) {
    try {
      lock.writeLock().lock();
      return supplier.get();
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void writeGuard(Runnable runnable) {
    writeGuard(() -> {
      runnable.run();
      return null;
    });
  }
}
