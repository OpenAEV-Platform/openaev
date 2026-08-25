package io.openaev.config.cache;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.config.MarkingScopeResolver;
import io.openaev.config.MarkingScopeResolver.MarkingRef;
import io.openaev.context.MarkingCtx;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves and caches a caller's marking clearance for a tenant.
 *
 * <p><b>Why JDBC and not a repository.</b> This runs during request-argument resolution, which
 * happens <i>before</i> the {@code @Transactional} interceptor. Hibernate's default connection
 * handling releases a connection when the transaction completes; with no transaction there is no
 * such event, so the connection is held until the open-in-view session closes — the end of the
 * request. {@link TenantMembershipCacheManager} documents the same constraint for the tenant
 * dimension, and it is not theoretical: it is what trips {@code
 * spring.datasource.hikari.leak-detection-threshold}. {@code JdbcTemplate} borrows and returns per
 * statement.
 *
 * <p>🔴 <b>Eviction is a correctness requirement, not an optimisation.</b> {@code
 * is_marking_set_allowed} is pure set containment against the GUC — it never consults {@code
 * marking_definitions}. So a stale clearance that is <i>larger</i> than the current data justifies
 * grants access to rows that should now be hidden: it fails <b>open</b>. Every reduction must evict
 * — user removed from a group, marking unassigned from a group, group deleted, marking archived or
 * deleted, marking order lowered. The 5-minute TTL bounds the damage; it does not prevent it.
 */
@Service
@RequiredArgsConstructor
@AllowRawJdbc(
    reason =
        "scope-bootstrap read, in the same category as AutonomousRunTenantLocator: marking-scope"
            + " resolution runs during argument resolution, before any transaction and therefore"
            + " before a tenant scope exists, so the statement inspector cannot serve it. Hibernate"
            + " is unusable here for a second reason: with no transaction to release at, an ORM"
            + " query would pin the pool connection until the open-in-view session closes at the"
            + " end of the request (see TenantMembershipCacheManager, and"
            + " spring.datasource.hikari.leak-detection-threshold). marking_definitions IS a"
            + " tenant-active table, so both queries bind tenant_id explicitly and are read-only;"
            + " they return marking ids and ordering metadata only, never row payloads. The"
            + " explicit bind is what replaces the inspector here and is pinned by"
            + " MarkingClearanceCacheManagerTest.")
public class MarkingClearanceCacheManager {

  static final String MARKING_CLEARANCE_CACHE = "markingClearance";

  /**
   * The marking ids the user's groups grant, in the given tenant. Bound to the tenant on the
   * definition side: a group is platform-wide, but a marking belongs to exactly one tenant, so this
   * cannot leak a grant across tenants.
   */
  static final String GRANTED_MARKING_IDS_SQL =
      "select gm.marking_id from groups_markings gm"
          + " join users_groups ug on ug.group_id = gm.group_id"
          + " join marking_definitions md on md.marking_id = gm.marking_id"
          + " where ug.user_id = ? and md.tenant_id = ?";

  /** Every marking defined in the tenant — the scale the granted ids are expanded against. */
  static final String TENANT_MARKINGS_SQL =
      "select marking_id, marking_type, marking_order from marking_definitions"
          + " where tenant_id = ?";

  private final JdbcTemplate jdbcTemplate;
  private final MarkingScopeResolver resolver;

  /**
   * Returns the clearance the user holds in the tenant (cached).
   *
   * @param bypass whether the caller is admin or holds BYPASS; part of the cache key because it
   *     changes the answer, and a user can gain or lose it
   */
  @Cacheable(value = MARKING_CLEARANCE_CACHE, key = "#userId + ':' + #tenantId + ':' + #bypass")
  public MarkingCtx findClearance(String userId, String tenantId, boolean bypass) {
    List<MarkingRef> definitions =
        jdbcTemplate.query(
            TENANT_MARKINGS_SQL,
            (rs, rowNum) ->
                new MarkingRef(
                    rs.getString("marking_id"),
                    rs.getString("marking_type"),
                    rs.getInt("marking_order")),
            tenantId);

    // Skipped for a bypassing caller: the grants cannot change the answer, so the query is waste.
    Set<String> granted =
        bypass
            ? Set.of()
            : Set.copyOf(
                jdbcTemplate.queryForList(GRANTED_MARKING_IDS_SQL, String.class, userId, tenantId));

    return resolver.resolve(granted, definitions, bypass);
  }

  /**
   * Evicts one user's clearance in one tenant, both with and without bypass.
   *
   * <p>Use when the change is scoped to a single user — added to or removed from a group, granted
   * or stripped of BYPASS. Both variants are dropped deliberately: making the caller name the right
   * one would let a stale <i>larger</i> entry survive under the other, and that entry fails open.
   * The caller usually cannot know which variant is warm, and should not have to.
   */
  @Caching(
      evict = {
        @CacheEvict(value = MARKING_CLEARANCE_CACHE, key = "#userId + ':' + #tenantId + ':false'"),
        @CacheEvict(value = MARKING_CLEARANCE_CACHE, key = "#userId + ':' + #tenantId + ':true'")
      })
  public void evict(String userId, String tenantId) {
    // eviction only
  }

  /**
   * Evicts every cached clearance.
   *
   * <p>Blunt on purpose. The changes that matter — a marking unassigned from a group, a group
   * deleted, a definition archived, an order lowered — reduce the clearance of an unbounded set of
   * users, and the mapping from the change to that set is itself a query. Since a stale entry fails
   * open, over-evicting costs one JDBC round trip per affected user while under-evicting is a
   * disclosure. Narrow this only with a test that pins which users each change reaches.
   */
  @CacheEvict(value = MARKING_CLEARANCE_CACHE, allEntries = true)
  public void evictAll() {
    // eviction only
  }
}
