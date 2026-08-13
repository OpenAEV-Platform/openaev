package io.openaev.config.cache;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.database.repository.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Caches tenant membership checks to avoid hitting the database on every HTTP request. The cache
 * has a short TTL (5 minutes) and is explicitly evicted when users are added to or removed from
 * tenants.
 *
 * <p>{@link #findTenantIdsByUserId(String)} is a JDBC id-only lookup so argument resolution (and
 * other request-scoped callers) do not open a Hibernate session. Combined with open-in-view, a
 * Hibernate query here would hold a pool connection for the whole HTTP request and trip Hikari leak
 * detection on long POSTs.
 */
@Service
@RequiredArgsConstructor
@AllowRawJdbc(
    reason =
        "reads users_tenants.tenant_id joined to tenants.tenant_deleted_at for an explicit user_id"
            + " bind so TxCtx argument resolution can return ids without a Hibernate session; OSIV"
            + " would otherwise hold the pool connection for the whole HTTP request. Same"
            + " soft-delete predicate as TenantRepository.findTenantsByUserId; no tenant row"
            + " payloads are returned.")
public class TenantMembershipCacheManager {

  static final String TENANT_MEMBERSHIP_CACHE = "tenantMembership";
  static final String USER_TENANT_IDS_CACHE = "userTenantIds";

  /** Must stay aligned with {@code TenantRepository.findTenantsByUserId} (active tenants only). */
  static final String USER_TENANT_IDS_SQL =
      "select ut.tenant_id from users_tenants ut"
          + " join tenants t on t.tenant_id = ut.tenant_id"
          + " where ut.user_id = ?"
          + " and t.tenant_deleted_at is null"
          + " order by t.tenant_name";

  private final TenantRepository tenantRepository;
  private final JdbcTemplate jdbcTemplate;
  private final CacheManager cacheManager;

  /**
   * Returns whether the given user belongs to the given tenant (cached). The cache is explicitly
   * evicted when users are added to or removed from tenants, so membership changes take effect
   * immediately.
   */
  @Cacheable(value = TENANT_MEMBERSHIP_CACHE, key = "#userId + ':' + #tenantId")
  public boolean existsByUserIdAndTenantId(String userId, String tenantId) {
    return tenantRepository.existsByUserIdAndTenantId(userId, tenantId);
  }

  /**
   * Returns the tenant ids the user belongs to (cached). Uses JDBC so the connection is borrowed
   * and returned immediately, independent of Hibernate open-in-view.
   */
  @Cacheable(value = USER_TENANT_IDS_CACHE, key = "#userId")
  public List<String> findTenantIdsByUserId(String userId) {
    return List.copyOf(jdbcTemplate.queryForList(USER_TENANT_IDS_SQL, String.class, userId));
  }

  /**
   * Evicts a specific user-tenant membership entry and the user's cached tenant-id list after
   * membership changes.
   */
  @Caching(
      evict = {
        @CacheEvict(value = TENANT_MEMBERSHIP_CACHE, key = "#userId + ':' + #tenantId"),
        @CacheEvict(value = USER_TENANT_IDS_CACHE, key = "#userId")
      })
  public void evict(String userId, String tenantId) {
    // eviction only
  }

  /**
   * Evicts all cached tenant membership entries for a given user, including the cached tenant-id
   * list. Membership keys are evicted through {@link CacheManager} because calling {@link
   * #evict(String, String)} from this method would be a self-invocation and skip the cache
   * interceptor.
   */
  public void evictForUser(String userId, List<String> tenantIds) {
    Cache tenantIdsCache = cacheManager.getCache(USER_TENANT_IDS_CACHE);
    if (tenantIdsCache != null) {
      tenantIdsCache.evict(userId);
    }
    Cache membershipCache = cacheManager.getCache(TENANT_MEMBERSHIP_CACHE);
    if (membershipCache != null) {
      for (String tenantId : tenantIds) {
        membershipCache.evict(userId + ":" + tenantId);
      }
    }
  }
}
