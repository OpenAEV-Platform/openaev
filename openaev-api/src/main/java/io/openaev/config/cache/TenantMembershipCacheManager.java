package io.openaev.config.cache;

import io.openaev.database.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Caches tenant membership checks to avoid hitting the database on every HTTP request. The cache
 * has a short TTL (5 minutes) and is explicitly evicted when users are added to or removed from
 * tenants.
 */
@Service
@RequiredArgsConstructor
public class TenantMembershipCacheManager {

  private final TenantRepository tenantRepository;

  /**
   * Returns whether the given user belongs to the given tenant (cached). The cache is explicitly
   * evicted when users are added to or removed from tenants, so membership changes take effect
   * immediately.
   */
  @Cacheable(value = "tenantMembership", key = "#userId + ':' + #tenantId")
  public boolean existsByUserIdAndTenantId(String userId, String tenantId) {
    return tenantRepository.existsByUserIdAndTenantId(userId, tenantId);
  }

  /** Evicts a specific user–tenant entry after membership changes. */
  @CacheEvict(value = "tenantMembership", key = "#userId + ':' + #tenantId")
  public void evict(String userId, String tenantId) {
    // eviction only
  }
}
