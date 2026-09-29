package io.openaev.config.cache;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Decorates a delegate {@link CacheManager} so every {@link Cache#evict(Object)} / {@link
 * Cache#clear()} call (declarative {@code @CacheEvict}/{@code @Caching}, or a direct {@code
 * cacheManager.getCache(name).evict(...)} call) is applied twice when a Spring-managed transaction
 * is active:
 *
 * <ol>
 *   <li><b>Immediately</b> — so a read in the same transaction (or a fully non-transactional
 *       caller) never sees the entry being evicted.
 *   <li><b>Again after the transaction commits</b> — a membership row inserted/removed by the
 *       transaction is not visible to other connections until commit (READ COMMITTED), so a
 *       concurrent transaction could repopulate the cache from the pre-commit state during the
 *       window before our commit. The post-commit eviction busts that possibly-stale repopulation,
 *       forcing the next read back to the database once the row is visible everywhere.
 * </ol>
 *
 * <p>Applied once at the {@link CacheManager} level (see {@code CachingConfig}), every cache — not
 * just tenant membership — gets this safety net for free, and callers go back to plain declarative
 * {@code @Cacheable}/{@code @CacheEvict}/{@code @Caching}: no {@link
 * TransactionSynchronizationManager} code is needed in service classes.
 */
public class CommitAwareCacheManager implements CacheManager {

  private final CacheManager delegate;
  private final ConcurrentHashMap<String, Cache> decoratedCaches = new ConcurrentHashMap<>();

  public CommitAwareCacheManager(CacheManager delegate) {
    this.delegate = delegate;
  }

  @Override
  @Nullable
  public Cache getCache(String name) {
    Cache target = delegate.getCache(name);
    if (target == null) {
      return null;
    }
    return decoratedCaches.computeIfAbsent(name, ignored -> new CommitAwareCache(target));
  }

  @Override
  public Collection<String> getCacheNames() {
    return delegate.getCacheNames();
  }

  /**
   * Wraps a single {@link Cache}, applying the immediate-plus-after-commit double eviction on
   * {@link #evict(Object)} and {@link #clear()}. {@link Cache#evictIfPresent(Object)} and {@link
   * Cache#invalidate()} get the same guarantee for free: their default implementations delegate to
   * {@code evict}/{@code clear} respectively.
   */
  private static final class CommitAwareCache implements Cache {

    private final Cache target;

    private CommitAwareCache(Cache target) {
      this.target = target;
    }

    @Override
    public String getName() {
      return target.getName();
    }

    @Override
    public Object getNativeCache() {
      return target.getNativeCache();
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
      return target.get(key);
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
      return target.get(key, type);
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
      return target.get(key, valueLoader);
    }

    @Override
    public void put(Object key, @Nullable Object value) {
      target.put(key, value);
    }

    @Override
    public void evict(Object key) {
      target.evict(key);
      afterCommit(() -> target.evict(key));
    }

    @Override
    public void clear() {
      target.clear();
      afterCommit(target::clear);
    }

    private void afterCommit(Runnable eviction) {
      if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        return;
      }
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              eviction.run();
            }
          });
    }
  }
}
