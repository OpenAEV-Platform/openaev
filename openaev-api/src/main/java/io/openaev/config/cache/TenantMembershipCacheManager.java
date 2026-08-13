package io.openaev.config.cache;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.database.repository.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
        "reads only users_tenants.tenant_id for an explicit user_id bind so TxCtx argument"
            + " resolution can return ids without a Hibernate session; OSIV would otherwise hold"
            + " the pool connection for the whole HTTP request. No other tenant rows are touched.")
public class TenantMembershipCacheManager {

  static final String USER_TENANT_IDS_SQL =
      "select ut.tenant_id from users_tenants ut where ut.user_id = ?";

  private final TenantRepository tenantRepository;
  private final JdbcTemplate jdbcTemplate;

  /**
   * Returns whether the given user belongs to the given tenant (cached). The cache is explicitly
   * evicted when users are added to or removed from tenants, so membership changes take effect
   * immediately.
   */
  @Cacheable(value = "tenantMembership", key = "#userId + ':' + #tenantId")
  public boolean existsByUserIdAndTenantId(String userId, String tenantId) {
    return tenantRepository.existsByUserIdAndTenantId(userId, tenantId);
  }

  /**
   * Returns the tenant ids the user belongs to (cached). Uses JDBC so the connection is borrowed
   * and returned immediately, independent of Hibernate open-in-view.
   */
  @Cacheable(value = "userTenantIds", key = "#userId")
  public List<String> findTenantIdsByUserId(String userId) {
    return jdbcTemplate.queryForList(USER_TENANT_IDS_SQL, String.class, userId);
  }

  /**
   * Evicts a specific user-tenant membership entry and the user's cached tenant-id list after
   * membership changes.
   */
  @Caching(
      evict = {
        @CacheEvict(value = "tenantMembership", key = "#userId + ':' + #tenantId"),
        @CacheEvict(value = "userTenantIds", key = "#userId")
      })
  public void evict(String userId, String tenantId) {
    // eviction only
  }

  /**
   * Evicts all cached tenant membership entries for a given user, including the cached tenant-id
   * list.
   */
  @CacheEvict(value = "userTenantIds", key = "#userId")
  public void evictForUser(String userId, List<String> tenantIds) {
    for (String tenantId : tenantIds) {
      evict(userId, tenantId);
    }
  }
}
