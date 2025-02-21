/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.sync.local;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.core.sync.Lock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalOnlyLock implements Lock {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalOnlyLock.class);

  private static final CacheLoader<String, Semaphore> LOADER =
      new CacheLoader<String, Semaphore>() {
        @Override
        public Semaphore load(String key) {
          return new Semaphore(1, true);
        }
      };
  private static final ConcurrentHashMap<String, ScheduledFuture<?>> SCHEDULEDFUTURES =
      new ConcurrentHashMap<>();
  private static final LoadingCache<String, Semaphore> LOCKIDTOSEMAPHOREMAP =
      Caffeine.newBuilder().build(LOADER);
  private static final ThreadGroup THREAD_GROUP = new ThreadGroup("LocalOnlyLock-scheduler");
  private static final ThreadFactory THREAD_FACTORY =
      runnable -> new Thread(THREAD_GROUP, runnable);
  private static final ScheduledExecutorService SCHEDULER =
      Executors.newScheduledThreadPool(1, THREAD_FACTORY);

  @Override
  public void acquireLock(String lockId) {
    LOGGER.trace("Locking {}", lockId);
    LOCKIDTOSEMAPHOREMAP.get(lockId).acquireUninterruptibly();
  }

  @Override
  public boolean acquireLock(@Nullable String lockId, long timeToTry, TimeUnit unit) {
    try {
      LOGGER.trace("Locking {} with timeout {} {}", lockId, timeToTry, unit);
      return LOCKIDTOSEMAPHOREMAP.get(lockId).tryAcquire(timeToTry, unit);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean acquireLock(
      @Nullable String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
    LOGGER.trace(
        "Locking {} with timeout {} {} for {} {}", lockId, timeToTry, unit, leaseTime, unit);
    if (acquireLock(lockId, timeToTry, unit)) {
      LOGGER.trace("Releasing {} automatically after {} {}", lockId, leaseTime, unit);
      SCHEDULEDFUTURES.put(lockId, SCHEDULER.schedule(() -> releaseLock(lockId), leaseTime, unit));
      return true;
    }
    return false;
  }

  private void removeLeaseExpirationJob(@Nullable String lockId) {
    ScheduledFuture<?> schedFuture = SCHEDULEDFUTURES.get(lockId);
    if (schedFuture != null && schedFuture.cancel(false)) {
      SCHEDULEDFUTURES.remove(lockId);
      LOGGER.trace("lockId {} removed from lease expiration job", lockId);
    }
  }

  @Override
  public void releaseLock(@Nullable String lockId) {
    // Synchronized to prevent race condition between semaphore check and actual release
    // The check is here to prevent semaphore getting above 1
    // e.g. in case when lease runs out but release is also called
    synchronized (LOCKIDTOSEMAPHOREMAP) {
      if (LOCKIDTOSEMAPHOREMAP.get(lockId).availablePermits() == 0) {
        LOGGER.trace("Releasing {}", lockId);
        LOCKIDTOSEMAPHOREMAP.get(lockId).release();
        removeLeaseExpirationJob(lockId);
      }
    }
  }

  @Override
  public void deleteLock(@Nullable String lockId) {
    LOGGER.trace("Deleting {}", lockId);
    LOCKIDTOSEMAPHOREMAP.invalidate(lockId);
  }

  @VisibleForTesting
  LoadingCache<String, Semaphore> cache() {
    return LOCKIDTOSEMAPHOREMAP;
  }

  @VisibleForTesting
  ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutures() {
    return SCHEDULEDFUTURES;
  }
}
