package io.openaev.config.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.config.CachingConfig;
import io.openaev.config.MarkingScopeResolver;
import io.openaev.config.MarkingScopeResolver.MarkingRef;
import io.openaev.context.MarkingCtx;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Caching and eviction contract of {@link MarkingClearanceCacheManager}.
 *
 * <p>🔴 Eviction here is a <b>correctness</b> requirement, not an optimisation. {@code
 * is_marking_set_allowed} is pure set containment against the GUC — it never consults {@code
 * marking_definitions} — so a stale clearance that is <i>larger</i> than the current data justifies
 * grants access to rows that should now be hidden. It fails <b>open</b>. The tests below make that
 * visible rather than asserting it in a comment.
 */
@SpringBootTest(
    classes = {CachingConfig.class, MarkingClearanceCacheManager.class, MarkingScopeResolver.class})
@DisplayName("marking clearance caching, where a stale entry fails open")
class MarkingClearanceCacheManagerCachingTest {

  private static final String USER = "user-1";
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private static final MarkingRef TLP_GREEN = new MarkingRef("tlp-green", "TLP", 20);
  private static final MarkingRef TLP_RED = new MarkingRef("tlp-red", "TLP", 50);

  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private JdbcTemplate jdbcTemplate;
  @MockitoBean private TenantMembershipCacheManager tenantMembershipCacheManager;

  @BeforeEach
  void clearCache() {
    var cache = cacheManager.getCache("markingClearance");
    if (cache != null) {
      cache.clear();
    }
    reset(jdbcTemplate);
  }

  /** Stubs the tenant's scale and the ids the user is granted on it. */
  private void givenGrants(String tenantId, List<MarkingRef> scale, List<String> granted) {
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(tenantId))).thenReturn(scale);
    lenient()
        .when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(USER), eq(tenantId)))
        .thenReturn(granted);
  }

  @Nested
  @DisplayName("caching")
  class Caching {

    @Test
    @DisplayName(
        "given a second call, should serve it from the cache without touching the database")
    void given_secondCall_should_notHitTheDatabase() {
      // -- ARRANGE --
      givenGrants(TENANT_A, List.of(TLP_GREEN, TLP_RED), List.of("tlp-green"));

      // -- ACT --
      MarkingCtx first = clearanceCache.findClearance(USER, TENANT_A, false);
      MarkingCtx second = clearanceCache.findClearance(USER, TENANT_A, false);

      // -- ASSERT --
      assertThat(first.toGuc()).isEqualTo("tlp-green");
      assertThat(second).isEqualTo(first);
      verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), eq(TENANT_A));
    }

    @Test
    @DisplayName("given the same user in two tenants, should not share one clearance")
    void given_twoTenants_should_keyTheCacheSeparately() {
      // -- ARRANGE --
      // The same user may legitimately hold RED in one tenant and nothing in another.
      givenGrants(TENANT_A, List.of(TLP_GREEN, TLP_RED), List.of("tlp-red"));
      givenGrants(TENANT_B, List.of(TLP_GREEN, TLP_RED), List.of());

      // -- ACT --
      MarkingCtx inA = clearanceCache.findClearance(USER, TENANT_A, false);
      MarkingCtx inB = clearanceCache.findClearance(USER, TENANT_B, false);

      // -- ASSERT --
      // A tenant-blind cache key would carry tenant A's clearance into tenant B: a cross-tenant
      // widening that no SQL predicate would catch, because the GUC would simply be wrong.
      assertThat(inA.toGuc()).isEqualTo("tlp-green,tlp-red");
      assertThat(inB).isEqualTo(MarkingCtx.none());
    }

    @Test
    @DisplayName("given the same user with and without bypass, should not share one clearance")
    void given_bypassDifference_should_keyTheCacheSeparately() {
      // -- ARRANGE --
      givenGrants(TENANT_A, List.of(TLP_GREEN, TLP_RED), List.of());

      // -- ACT --
      MarkingCtx asUser = clearanceCache.findClearance(USER, TENANT_A, false);
      MarkingCtx asBypass = clearanceCache.findClearance(USER, TENANT_A, true);

      // -- ASSERT --
      // Losing BYPASS must not leave the wider clearance behind under the same key.
      assertThat(asUser).isEqualTo(MarkingCtx.none());
      assertThat(asBypass.toGuc()).isEqualTo("tlp-green,tlp-red");
    }
  }

  @Nested
  @DisplayName("eviction")
  class Eviction {

    @Test
    @DisplayName("given a clearance reduced in the database, should keep serving the wider one")
    void given_reducedClearance_should_failOpenUntilEvicted() {
      // -- ARRANGE --
      // The user holds RED, and reads it once so the entry is warm.
      givenGrants(TENANT_A, List.of(TLP_GREEN, TLP_RED), List.of("tlp-red"));
      assertThat(clearanceCache.findClearance(USER, TENANT_A, false).toGuc())
          .isEqualTo("tlp-green,tlp-red");

      // -- ACT --
      // RED is now unassigned from their group: the database says GREEN.
      givenGrants(TENANT_A, List.of(TLP_GREEN, TLP_RED), List.of("tlp-green"));
      MarkingCtx stale = clearanceCache.findClearance(USER, TENANT_A, false);

      // -- ASSERT --
      // This is the fail-open window, asserted deliberately: the cached clearance still contains
      // tlp-red, so is_marking_set_allowed keeps returning true for RED rows. Nothing downstream
      // can detect this — the predicate never consults marking_definitions. Only eviction fixes it.
      assertThat(stale.toGuc()).isEqualTo("tlp-green,tlp-red");

      // -- ACT --
      clearanceCache.evict(USER, TENANT_A);

      // -- ASSERT --
      assertThat(clearanceCache.findClearance(USER, TENANT_A, false).toGuc())
          .isEqualTo("tlp-green");
    }

    @Test
    @DisplayName("given evict, should re-read only the affected user and tenant")
    void given_evict_should_beScopedToOneEntry() {
      // -- ARRANGE --
      givenGrants(TENANT_A, List.of(TLP_GREEN), List.of("tlp-green"));
      givenGrants(TENANT_B, List.of(TLP_GREEN), List.of("tlp-green"));
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_B, false);

      // -- ACT --
      clearanceCache.evict(USER, TENANT_A);
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_B, false);

      // -- ASSERT --
      verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class), eq(TENANT_A));
      verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), eq(TENANT_B));
    }

    @Test
    @DisplayName("given evict, should drop the bypass variant too, not just the one named")
    void given_evict_should_dropBothBypassVariants() {
      // -- ARRANGE --
      // Both variants warm. If evict dropped only one, the surviving entry would be the WIDER of
      // the two (bypass), which is precisely the fail-open case eviction exists to prevent.
      givenGrants(TENANT_A, List.of(TLP_GREEN, TLP_RED), List.of("tlp-green"));
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_A, true);

      // -- ACT --
      clearanceCache.evict(USER, TENANT_A);
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_A, true);

      // -- ASSERT --
      // Two reads before, two after: neither variant survived.
      verify(jdbcTemplate, times(4)).query(anyString(), any(RowMapper.class), eq(TENANT_A));
    }

    @Test
    @DisplayName("given a membership change, should drop the user's clearance in EVERY tenant")
    void given_evictForUser_should_reachEveryTenant() {
      // -- ARRANGE --
      // A Group is dual-scope: a platform group grants markings across tenants, and users_groups
      // carries no tenant. So dropping a user from one reduces their clearance everywhere at once.
      // evict(user, ONE tenant) would leave the others stale, and stale-larger fails open.
      givenGrants(TENANT_A, List.of(TLP_GREEN), List.of("tlp-green"));
      givenGrants(TENANT_B, List.of(TLP_RED), List.of("tlp-red"));
      when(tenantMembershipCacheManager.findTenantIdsByUserId(USER))
          .thenReturn(List.of(TENANT_A, TENANT_B));
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_B, false);

      // -- ACT --
      clearanceCache.evictForUser(USER);
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_B, false);

      // -- ASSERT --
      verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class), eq(TENANT_A));
      verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class), eq(TENANT_B));
    }

    @Test
    @DisplayName("given a membership change, should drop the bypass variant in every tenant too")
    void given_evictForUser_should_dropBothVariantsPerTenant() {
      // -- ARRANGE --
      givenGrants(TENANT_A, List.of(TLP_GREEN), List.of("tlp-green"));
      when(tenantMembershipCacheManager.findTenantIdsByUserId(USER)).thenReturn(List.of(TENANT_A));
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_A, true);

      // -- ACT --
      clearanceCache.evictForUser(USER);
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_A, true);

      // -- ASSERT --
      verify(jdbcTemplate, times(4)).query(anyString(), any(RowMapper.class), eq(TENANT_A));
    }

    @Test
    @DisplayName("given a tenant the user cannot reach, should not fail resolving it")
    void given_evictForUser_should_tolerateAnEmptyTenantList() {
      // -- ARRANGE --
      // A user with no tenants is a normal state, not an error: nothing to evict.
      when(tenantMembershipCacheManager.findTenantIdsByUserId(USER)).thenReturn(List.of());

      // -- ACT & ASSERT --
      assertDoesNotThrow(() -> clearanceCache.evictForUser(USER));
    }

    @Test
    @DisplayName("given evictForUsers, should reach every user named")
    void given_evictForUsers_should_reachEveryUser() {
      // -- ARRANGE --
      // The group paths evict a whole membership list at once.
      when(tenantMembershipCacheManager.findTenantIdsByUserId(anyString()))
          .thenReturn(List.of(TENANT_A));

      // -- ACT --
      clearanceCache.evictForUsers(List.of(USER, "user-2", "user-3"));

      // -- ASSERT --
      verify(tenantMembershipCacheManager).findTenantIdsByUserId(USER);
      verify(tenantMembershipCacheManager).findTenantIdsByUserId("user-2");
      verify(tenantMembershipCacheManager).findTenantIdsByUserId("user-3");
    }

    @Test
    @DisplayName("given evictAll, should drop every entry")
    void given_evictAll_should_dropEverything() {
      // -- ARRANGE --
      // The blunt eviction covers the changes that reduce an unbounded set of users at once:
      // a marking unassigned from a group, a group deleted, a definition archived.
      givenGrants(TENANT_A, List.of(TLP_GREEN), List.of("tlp-green"));
      givenGrants(TENANT_B, List.of(TLP_GREEN), List.of("tlp-green"));
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_B, false);

      // -- ACT --
      clearanceCache.evictAll();
      clearanceCache.findClearance(USER, TENANT_A, false);
      clearanceCache.findClearance(USER, TENANT_B, false);

      // -- ASSERT --
      verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class), eq(TENANT_A));
      verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class), eq(TENANT_B));
    }
  }

  @Nested
  @DisplayName("registration")
  class Registration {

    @Test
    @DisplayName("the cache must be registered, or @Cacheable silently does nothing")
    void cacheIsRegistered() {
      assertThat(cacheManager.getCache("markingClearance")).isNotNull();
    }
  }
}
