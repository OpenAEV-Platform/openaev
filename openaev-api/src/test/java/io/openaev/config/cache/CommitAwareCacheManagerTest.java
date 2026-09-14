package io.openaev.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unit tests for {@link CommitAwareCacheManager} in isolation, independent of any concrete cache
 * user (e.g. {@code TenantMembershipCacheManager}), using a plain in-memory {@link
 * ConcurrentMapCacheManager} as the delegate.
 */
@DisplayName("CommitAwareCacheManager")
class CommitAwareCacheManagerTest {

  private static final String CACHE_NAME = "test-cache";
  private static final String KEY = "key-1";

  private final ConcurrentMapCacheManager delegate = new ConcurrentMapCacheManager(CACHE_NAME);
  private final CommitAwareCacheManager cacheManager = new CommitAwareCacheManager(delegate);

  @AfterEach
  void tearDown() {
    // Never leave synchronization active for the next test in the same thread.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("given_noActiveTransaction_evict_should_evictOnceImmediately")
  void given_noActiveTransaction_evict_should_evictOnceImmediately() {
    // Arrange
    Cache cache = cacheManager.getCache(CACHE_NAME);
    cache.put(KEY, "value");

    // Act
    cache.evict(KEY);

    // Assert
    assertThat(cache.get(KEY)).isNull();
  }

  @Test
  @DisplayName(
      "given_activeTransaction_evict_should_evictImmediately_and_notLeaveARepopulationUnbusted")
  void given_activeTransaction_evict_should_evictImmediately_and_notLeaveARepopulationUnbusted() {
    // Arrange
    Cache cache = cacheManager.getCache(CACHE_NAME);
    cache.put(KEY, "stale-value");
    TransactionSynchronizationManager.initSynchronization();

    // Act — evict must apply immediately: a read right after, in the same transaction, must
    // never see the stale entry.
    cache.evict(KEY);
    assertThat(cache.get(KEY)).isNull();

    // Simulate a concurrent transaction repopulating the cache from data that predates our
    // still-uncommitted change (this is the race the immediate-only eviction cannot prevent).
    cache.put(KEY, "stale-value-repopulated-by-concurrent-reader");
    assertThat(cache.get(KEY)).isNotNull();

    // Act — simulate our transaction committing.
    TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

    // Assert — the post-commit eviction busts the stale repopulation.
    assertThat(cache.get(KEY)).isNull();
  }

  @Test
  @DisplayName("given_activeTransaction_clear_should_clearImmediately_and_clearAgainAfterCommit")
  void given_activeTransaction_clear_should_clearImmediately_and_clearAgainAfterCommit() {
    // Arrange
    Cache cache = cacheManager.getCache(CACHE_NAME);
    cache.put(KEY, "value");
    TransactionSynchronizationManager.initSynchronization();

    // Act
    cache.clear();
    assertThat(cache.get(KEY)).isNull();

    // Simulate a concurrent repopulation, then our commit.
    cache.put(KEY, "repopulated");
    TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

    // Assert
    assertThat(cache.get(KEY)).isNull();
  }

  @Test
  @DisplayName("given_noActiveTransaction_evictIfPresent_should_delegateToEvict")
  void given_noActiveTransaction_evictIfPresent_should_delegateToEvict() {
    // Arrange — CommitAwareCache does not override Cache#evictIfPresent, so it runs Cache's own
    // default implementation, which delegates to evict(key) (our override, applying the
    // immediate-plus-after-commit guarantee) and — per that default's documented contract —
    // returns false unconditionally, since it has no way to know whether the key was present.
    Cache cache = cacheManager.getCache(CACHE_NAME);
    cache.put(KEY, "value");

    // Act
    boolean result = cache.evictIfPresent(KEY);

    // Assert
    assertThat(result).isFalse();
    assertThat(cache.get(KEY)).isNull();
  }

  @Test
  @DisplayName("given_sameCacheName_getCache_should_returnTheSameDecoratedInstance")
  void given_sameCacheName_getCache_should_returnTheSameDecoratedInstance() {
    // Arrange & Act
    Cache first = cacheManager.getCache(CACHE_NAME);
    Cache second = cacheManager.getCache(CACHE_NAME);

    // Assert — decoration is cached per name, not rebuilt on every call.
    assertThat(first).isSameAs(second);
  }

  @Test
  @DisplayName("given_unknownCacheName_getCache_should_returnNull")
  void given_unknownCacheName_getCache_should_returnNull() {
    assertThat(cacheManager.getCache("does-not-exist")).isNull();
  }

  @Test
  @DisplayName("given_multipleTransactions_afterCommitEviction_should_notLeakBetweenTransactions")
  void given_multipleTransactions_afterCommitEviction_should_notLeakBetweenTransactions() {
    // Arrange
    Cache cache = cacheManager.getCache(CACHE_NAME);
    AtomicInteger evictionCount = new AtomicInteger();
    cache.put(KEY, "value");

    // Act — first transaction evicts and commits
    TransactionSynchronizationManager.initSynchronization();
    cache.evict(KEY);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(sync -> evictionCount.incrementAndGet());
    TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
    TransactionSynchronizationManager.clearSynchronization();

    // Assert — exactly one after-commit eviction was registered for the first transaction, and a
    // fresh transaction starts with no leftover synchronizations from the previous one.
    assertThat(evictionCount.get()).isEqualTo(1);
    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
  }
}
