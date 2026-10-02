package io.openaev.rest.collector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CollectorType;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code CollectorService#ensureCollectorTypeExists} must dedupe against the tenant it is about to
 * write for, not the ambient scope of the surrounding transaction. {@code collector_type_name} is
 * unique per tenant (migration V4_92), so two tenants can legitimately hold a same-named row; a
 * lookup scoped to the ambient {@code app.current_tenants} (which a multi-tenant background caller
 * can widen) can return the OTHER tenant's row and link it as the FK of a row being created for a
 * different tenant.
 *
 * <p>Today's only HTTP caller ({@code CollectorApi#registerCollector}) cannot reach this with a
 * genuinely multi-tenant scope: {@code TenantWriteScopeResolver#tenantForWrite} refuses an
 * ambiguous (size &gt; 1) scope before the service is ever called (see {@code
 * CollectorTypeHttpIsolationTest} for the HTTP-level coverage). This test drives the service
 * directly, under an explicit multi-tenant background scope, because the defect is in {@code
 * CollectorService} itself and nothing stops a future background caller (a per-tenant migration, an
 * onboarding step) from reaching it with a wider scope than the tenant it writes for.
 *
 * <p>The dedup must also FIND the write tenant's own row when it is there. A lookup that misses it
 * concludes the type is absent and re-inserts it, which violates {@code
 * collector_types_name_tenant_unique}. That was the shape behind the repeated unique-violation
 * errors the shadow runs reported while this table was read through the name-only lookup.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@link TenantScopedTransaction#execute} refuses to
 * open inside an active transaction. Seed and clean through auto-committed JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=collector_types")
@DisplayName("CollectorService.ensureCollectorTypeExists: dedup must target the write tenant")
class CollectorServiceMultiTenantScopeTest extends IntegrationTest {

  @Autowired private CollectorService collectorService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("collector-type-scope-a");
    tenantB = seedTenant("collector-type-scope-b");
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM collector_types WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Test
  @DisplayName(
      "given tenant B already owns a same-named collector type, ensuring it for tenant A under a"
          + " scope spanning both must create A's own row, never reuse B's")
  void given_sameNamedTypeOwnedByOtherTenantInScope_should_createOwnRowForWriteTenant() {
    // Arrange
    String typeName = "openaev_scope_test_" + UUID.randomUUID();
    seedCollectorType(tenantB, typeName);

    // Act
    CollectorType result =
        tenantTx.execute(
            TxCtx.forTenants(List.of(tenantA, tenantB)),
            () -> collectorService.ensureCollectorTypeExists(tenantA, typeName));

    // Assert
    assertEquals(
        tenantA,
        result.getTenant().getId(),
        "the collector type created for tenant A's write must belong to tenant A");
    assertNotEquals(
        rawCollectorTypeId(typeName, tenantB),
        result.getId(),
        "tenant A's row must not be tenant B's pre-existing row");
  }

  @Test
  @DisplayName(
      "given both tenants already own a same-named collector type, ensuring it for tenant A under a"
          + " scope spanning both must return A's own row and insert nothing")
  void given_bothTenantsOwnTheType_should_returnTheWriteTenantRowAndInsertNothing() {
    // Arrange
    String typeName = "openaev_scope_test_" + UUID.randomUUID();
    seedCollectorType(tenantA, typeName);
    seedCollectorType(tenantB, typeName);
    String expectedId = rawCollectorTypeId(typeName, tenantA);

    // Act
    CollectorType result =
        tenantTx.execute(
            TxCtx.forTenants(List.of(tenantA, tenantB)),
            () -> collectorService.ensureCollectorTypeExists(tenantA, typeName));

    // Assert
    assertEquals(
        expectedId,
        result.getId(),
        "the dedup must return tenant A's existing row, not tenant B's same-named one");
    assertEquals(
        2,
        rawCollectorTypeCount(typeName),
        "nothing may be inserted: the dedup must resolve A's own row by (name, tenant) and leave"
            + " the two rows as they are");
  }

  private int rawCollectorTypeCount(String typeName) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM collector_types WHERE collector_type_name = ?",
        Integer.class,
        typeName);
  }

  private String seedTenant(String label) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        label + "-" + id);
    return id;
  }

  private void seedCollectorType(String tenantId, String typeName) {
    jdbc.update(
        "INSERT INTO collector_types (collector_type_id, collector_type_name, tenant_id)"
            + " VALUES (?, ?, ?)",
        UUID.randomUUID().toString(),
        typeName,
        tenantId);
  }

  private String rawCollectorTypeId(String typeName, String tenantId) {
    return jdbc.queryForObject(
        "SELECT collector_type_id FROM collector_types WHERE collector_type_name = ? AND"
            + " tenant_id = ?",
        String.class,
        typeName,
        tenantId);
  }
}
