package io.openaev.config.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.config.MarkingScopeResolver;
import io.openaev.config.MarkingScopeResolver.MarkingRef;
import io.openaev.context.MarkingCtx;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Pins the raw-JDBC exemption of {@link MarkingClearanceCacheManager}.
 *
 * <p>{@code marking_definitions} is a tenant-active table, so raw JDBC bypasses the statement
 * inspector that would otherwise scope it. That is sanctioned here only because the read bootstraps
 * the scope (it runs during argument resolution, before any transaction exists) — the same
 * chicken-and-egg the {@code AutonomousRunTenantLocator} exemption rests on. What replaces the
 * inspector is an explicit {@code tenant_id} bind in both statements, so this test pins that bind:
 * dropping it would silently resolve a clearance from every tenant's markings at once.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("marking clearance resolution stays a pinned, tenant-bound scope-bootstrap read")
class MarkingClearanceCacheManagerTest {

  private static final String USER = "user-1";
  private static final String TENANT = "tenant-1";

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private CacheManager cacheManager;
  @Mock private TenantMembershipCacheManager tenantMembershipCacheManager;

  private final MarkingScopeResolver resolver = new MarkingScopeResolver();

  private MarkingClearanceCacheManager manager() {
    return new MarkingClearanceCacheManager(
        jdbcTemplate, resolver, cacheManager, tenantMembershipCacheManager);
  }

  @Nested
  @DisplayName("the SQL exemption")
  class SqlExemption {

    @Test
    @DisplayName("both statements must filter on tenant_id, the inspector's replacement")
    void bothStatementsAreTenantBound() {
      // -- ACT & ASSERT --
      assertTrue(
          MarkingClearanceCacheManager.TENANT_MARKINGS_SQL.contains("where tenant_id = ?"),
          "the definitions read must be tenant-bound");
      assertTrue(
          MarkingClearanceCacheManager.GRANTED_MARKING_IDS_SQL.contains("md.tenant_id = ?"),
          "the grants read must be tenant-bound through the definition side");
    }

    @Test
    @DisplayName("both statements must be read-only SELECTs")
    void bothStatementsAreReadOnly() {
      // -- ACT & ASSERT --
      // A write here would bypass the inspector's write predicate as well as its read one.
      for (String sql :
          List.of(
              MarkingClearanceCacheManager.TENANT_MARKINGS_SQL,
              MarkingClearanceCacheManager.GRANTED_MARKING_IDS_SQL)) {
        assertTrue(sql.trim().toLowerCase().startsWith("select"), sql);
        assertFalse(sql.toLowerCase().matches(".*\\b(insert|update|delete|merge)\\b.*"), sql);
      }
    }

    @Test
    @DisplayName("the exemption is declared, with a reason")
    void exemptionIsDeclared() {
      // -- ACT --
      AllowRawJdbc annotation =
          MarkingClearanceCacheManager.class.getAnnotation(AllowRawJdbc.class);

      // -- ASSERT --
      assertNotNull(annotation, "raw JDBC on a tenant-active table must opt out explicitly");
      assertThat(annotation.reason()).contains("tenant_id");
    }
  }

  @Nested
  @DisplayName("resolution")
  class Resolution {

    @Test
    @DisplayName("given a granted marking, should pass the tenant to both queries")
    void given_aGrant_should_bindTheTenantEverywhere() {
      // -- ARRANGE --
      when(jdbcTemplate.query(
              eq(MarkingClearanceCacheManager.TENANT_MARKINGS_SQL),
              any(RowMapper.class),
              eq(TENANT)))
          .thenReturn(List.of(new MarkingRef("tlp-green", "TLP", 20)));
      when(jdbcTemplate.queryForList(
              eq(MarkingClearanceCacheManager.GRANTED_MARKING_IDS_SQL),
              eq(String.class),
              eq(USER),
              eq(TENANT)))
          .thenReturn(List.of("tlp-green"));

      // -- ACT --
      MarkingCtx clearance = manager().findClearance(USER, TENANT, false);

      // -- ASSERT --
      assertEquals("tlp-green", clearance.toGuc());
      verify(jdbcTemplate).queryForList(anyString(), eq(String.class), eq(USER), eq(TENANT));
    }

    @Test
    @DisplayName("given a bypassing caller, should skip the grants query entirely")
    void given_bypass_should_notQueryGrants() {
      // -- ARRANGE --
      when(jdbcTemplate.query(
              eq(MarkingClearanceCacheManager.TENANT_MARKINGS_SQL),
              any(RowMapper.class),
              eq(TENANT)))
          .thenReturn(List.of(new MarkingRef("tlp-red", "TLP", 50)));

      // -- ACT --
      MarkingCtx clearance = manager().findClearance(USER, TENANT, true);

      // -- ASSERT --
      // The grants cannot change a bypassing caller's answer, so the round trip is waste.
      assertEquals("tlp-red", clearance.toGuc());
      verify(jdbcTemplate, never())
          .queryForList(anyString(), eq(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("given a user with no grants, should resolve to none() and not fail")
    void given_noGrants_should_resolveToNone() {
      // -- ARRANGE --
      when(jdbcTemplate.query(
              eq(MarkingClearanceCacheManager.TENANT_MARKINGS_SQL),
              any(RowMapper.class),
              eq(TENANT)))
          .thenReturn(List.of(new MarkingRef("tlp-green", "TLP", 20)));
      when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(USER), eq(TENANT)))
          .thenReturn(List.of());

      // -- ACT & ASSERT --
      assertEquals(MarkingCtx.none(), manager().findClearance(USER, TENANT, false));
    }
  }
}
