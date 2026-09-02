package io.openaev.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The marking half of the background transaction primitive: a background transaction runs at
 * <b>system clearance</b> — every marking of the tenants in scope.
 *
 * <p>Note the asymmetry with the tenant dimension, because it is the point of this test rather than
 * an accident. The primitive <i>narrows</i> a job to its tenants, since a tenant is a real boundary
 * it must respect. It <i>widens</i> it to all markings, since a marking is a boundary between
 * <b>users</b> and a scheduler is not a user. Concretely: activating a table on marking must be a
 * no-op for background work — an inject targeting a TLP:RED asset still executes.
 *
 * <p>This also pins the allowlist entry in {@code
 * TenantActiveTableAccessArchTest#marking_definitions_repository_access_is_reviewed}: the
 * primitive's read of {@code marking_definitions} is properly tenant-scoped, and this test is what
 * makes that claim checkable rather than a comment.
 *
 * <p>Deliberately NOT {@code @Transactional}: the primitive opens its own transactions.
 */
@DisplayName("TenantScopedTransaction: the marking scope a background transaction runs at")
class TenantScopedTransactionMarkingScopeTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenantsWithDifferentScales() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("marking-scope-a-" + UUID.randomUUID());
    tenantB = seedTenant("marking-scope-b-" + UUID.randomUUID());
    seedMarking(tenantA, "TLP", "TLP:GREEN", 20);
    seedMarking(tenantA, "TLP", "TLP:RED", 50);
    seedMarking(tenantB, "PAP", "PAP:AMBER", 30);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM marking_definitions WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Nested
  @DisplayName("system clearance")
  class SystemClearance {

    @Test
    @DisplayName("given a tenant scope, should carry every marking of that tenant")
    void given_tenantScope_should_carryEveryMarkingOfTheTenant() {
      // -- ACT --
      Set<String> inside = tenantTx.execute(TxCtx.forTenant(tenantA), () -> currentMarkingScope());

      // -- ASSERT --
      // Not "the highest", not "none": all of them. A job is not a user, so it holds the whole
      // scale — which is what makes activating a marked table invisible to background work.
      assertEquals(markingIdsOf(tenantA), inside);
    }

    @Test
    @DisplayName("given a tenant scope, should NOT carry another tenant's markings")
    void given_tenantScope_should_notCarryAnotherTenantsMarkings() {
      // -- ACT --
      Set<String> inside = tenantTx.execute(TxCtx.forTenant(tenantA), () -> currentMarkingScope());

      // -- ASSERT --
      // The widening is per-tenant. Marking is a boundary between users; tenant is still a wall.
      assertTrue(
          Set.copyOf(markingIdsOf(tenantB)).stream().noneMatch(inside::contains),
          "tenant B's markings must not leak into tenant A's system clearance: " + inside);
    }

    @Test
    @DisplayName("given a multi-tenant scope, should carry the union of both scales")
    void given_multiTenantScope_should_carryTheUnion() {
      // -- ACT --
      Set<String> inside =
          tenantTx.execute(
              TxCtx.forTenants(List.of(tenantA, tenantB)), () -> currentMarkingScope());

      // -- ASSERT --
      assertTrue(
          inside.containsAll(markingIdsOf(tenantA)), "tenant A's scale is missing: " + inside);
      assertTrue(
          inside.containsAll(markingIdsOf(tenantB)), "tenant B's scale is missing: " + inside);
    }

    @Test
    @DisplayName("given a tenant with no markings, should carry an empty scope, not fail")
    void given_tenantWithoutMarkings_should_carryEmptyScope() {
      // -- ARRANGE --
      String bare = seedTenant("marking-scope-bare-" + UUID.randomUUID());
      try {
        // -- ACT --
        String inside =
            tenantTx.execute(TxCtx.forTenant(bare), () -> currentSetting("app.current_markings"));

        // -- ASSERT --
        // Correct rather than degraded: with nothing marked, every row is unmarked and visible.
        assertEquals("", inside);
      } finally {
        jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", bare);
      }
    }
  }

  @Nested
  @DisplayName("the scope channel")
  class ScopeChannel {

    @Test
    @DisplayName("given the transaction ended, should leave no marking scope behind")
    void given_transactionEnded_should_leaveNothingBehind() {
      // -- ARRANGE --
      tenantTx.execute(TxCtx.forTenant(tenantA), () -> currentMarkingScope());

      // -- ACT --
      String after = rawTransaction().execute(status -> currentSetting("app.current_markings"));

      // -- ASSERT --
      // set_config(..., true) is transaction-local: nothing survives onto a reused connection.
      assertEquals("", after);
    }

    @Test
    @DisplayName("given a raw transaction, should carry no marking scope at all")
    void given_rawTransaction_should_carryNoMarkingScope() {
      // -- ACT --
      String scope = rawTransaction().execute(status -> currentSetting("app.current_markings"));

      // -- ASSERT --
      // The primitive is the only door. Bypassing it yields an empty clearance — which for marking
      // means unmarked rows only, a narrowing rather than a leak.
      assertEquals("", scope);
    }
  }

  // -- HELPERS --

  private Set<String> currentMarkingScope() {
    String raw = currentSetting("app.current_markings");
    return raw.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(raw.split(",")));
  }

  private String currentSetting(String key) {
    return jdbc.queryForObject(
        "SELECT coalesce(current_setting('" + key + "', true), '')", String.class);
  }

  private Set<String> markingIdsOf(String tenantId) {
    return Set.copyOf(
        jdbc.queryForList(
            "SELECT marking_id FROM marking_definitions WHERE tenant_id = ?",
            String.class,
            tenantId));
  }

  private TransactionTemplate rawTransaction() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private void seedMarking(String tenantId, String type, String name, int order) {
    jdbc.update(
        "INSERT INTO marking_definitions (marking_id, marking_type, marking_name, marking_order,"
            + " marking_created_at, marking_updated_at, tenant_id)"
            + " VALUES (?, ?, ?, ?, now(), now(), ?)",
        UUID.randomUUID().toString(),
        type,
        name,
        order,
        tenantId);
  }
}
