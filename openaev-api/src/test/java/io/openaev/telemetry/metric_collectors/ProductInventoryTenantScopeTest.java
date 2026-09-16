package io.openaev.telemetry.metric_collectors;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import java.util.ArrayList;
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
 * The product inventory gauges are deliberately platform-wide: telemetry is per-instance, not
 * per-tenant. Under v1 that came for free, because the suppliers run on the OpenTelemetry exporter
 * thread outside any {@code @Transactional} method and the Hibernate {@code tenantFilter} is only
 * enabled by {@code HibernateFilterTransactionAspect} on a transactional call.
 *
 * <p>That reasoning is v1-specific and v2 falsifies it. {@code TenantStatementInspector} does not
 * care whether a transaction is active: with the table in {@code openaev.tenant.active-tables} and
 * {@code app.current_tenants} unset, {@code can_access_tenant} returns false and the count is zero.
 * The gauge keeps reporting, it just reports nothing, which is worse than failing.
 *
 * <p>This test pins the asset-group gauge across the {@code asset_groups} activation (#6435). The
 * class is not {@code @Transactional}: the scoped primitive refuses to open inside an active
 * transaction, so seeding goes through an auto-committing {@link JdbcTemplate}.
 */
@TestPropertySource(
    properties = "openaev.tenant.active-tables=asset_groups,assets,import_mappers,findings")
@DisplayName("product inventory gauges keep counting across tenants once a table is v2-active")
class ProductInventoryTenantScopeTest extends IntegrationTest {

  @Autowired private ProductInventoryMetricCollector collector;
  @Autowired private DataSource dataSource;
  @Autowired private io.openaev.database.repository.FindingRepository findingRepository;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private long baseline;
  private long endpointBaseline;
  private long mapperBaseline;
  private long findingBaseline;

  @BeforeEach
  void seedTwoTenantsWithOneAssetGroupEach() {
    jdbc = new JdbcTemplate(dataSource);
    // Raw JDBC bypasses Hibernate and therefore the inspector, so this sees every row whatever the
    // scope. Asserting on a DELTA rather than an absolute count keeps the test independent of what
    // else lives in the shared test database: never delete rows this test did not create.
    baseline = requireNonNull(jdbc.queryForObject("SELECT count(*) FROM asset_groups", Long.class));
    endpointBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM assets WHERE asset_type = 'Endpoint'", Long.class));
    mapperBaseline =
        requireNonNull(jdbc.queryForObject("SELECT count(*) FROM import_mappers", Long.class));
    findingBaseline =
        requireNonNull(jdbc.queryForObject("SELECT count(*) FROM findings", Long.class));
    seedAssetGroup(seedTenant("telemetry-a-" + UUID.randomUUID()), "telemetry-group-a");
    seedAssetGroup(seedTenant("telemetry-b-" + UUID.randomUUID()), "telemetry-group-b");
    seedEndpoint(seededTenants.get(0), "telemetry-vuln-endpoint-a");
    seedEndpoint(seededTenants.get(1), "telemetry-vuln-endpoint-b");
    seedImportMapper(seededTenants.get(0), "telemetry-mapper-a");
    // Findings in BOTH tenants. Without a seed the gauge assertion compared a zero baseline to a
    // zero count and held whether or not the supplier kept its TxCtx.allTenants() scope.
    seedFinding(seededTenants.get(0), "telemetry-finding-a");
    seedFinding(seededTenants.get(1), "telemetry-finding-b");
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM asset_groups WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM import_mappers WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("the asset-group gauge counts every tenant's rows, not zero")
  void assetGroupGaugeCountsAcrossTenants() {
    // Non-empty on purpose: asserting zero would stay green through exactly the fail-closed
    // regression this pins. Two tenants, one asset group each, one platform-wide count.
    assertEquals(
        baseline + 2L,
        collector.countAssetGroups(),
        "the platform-wide asset-group gauge must pick up both tenants' new rows once asset_groups"
            + " is v2-active; an unchanged or zero count here is the silent telemetry corruption"
            + " this test exists to catch");
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    seededTenants.add(id);
    return id;
  }

  @Test
  @DisplayName("the findings gauge counts every tenant's rows, not zero")
  void findingsGaugeCountsAcrossTenants() {
    // A delta against a baseline taken BEFORE seeding, like the asset-group case above. The first
    // version of this test read the baseline at assertion time and compared it to the gauge: on a
    // clean database that was 0 == 0, and it held whether or not the supplier kept its scope,
    // because the class seeded no findings at all. Two are seeded now, one per tenant.
    assertEquals(
        findingBaseline + 2L,
        collector.countFindings(),
        "findings_total must span every tenant; a zero here means the supplier lost its"
            + " TxCtx.allTenants() scope and the gauge silently stopped counting");
  }

  @Test
  @DisplayName("the same count without a scope returns zero: this is what the scope prevents")
  void findingsCountUnscopedIsZero() {
    // The red half. Without it the assertion above would also pass in a context where the
    // inspector never fires, and would prove nothing about scoping.
    assertEquals(
        0L,
        findingRepository.count(),
        "an unscoped count on an active table must be zero; if it now returns rows the inspector"
            + " stopped firing and the assertion above no longer proves anything");
  }

  private void seedAssetGroup(String tenantId, String name) {
    jdbc.update(
        "INSERT INTO asset_groups (asset_group_id, asset_group_name, asset_group_dynamic_filter,"
            + " asset_group_created_at, asset_group_updated_at, tenant_id)"
            + " VALUES (?, ?, CAST(? AS json), now(), now(), ?)",
        UUID.randomUUID().toString(),
        name,
        "{}",
        tenantId);
  }

  @Test
  @DisplayName("the scoped vulnerable-endpoint count spans every tenant, not zero")
  void vulnerableEndpointCountSpansTenants() {
    // This half proves the SCOPE works against a real database. That the gauge is actually wired to
    // this method rather than to the unscoped repository is asserted in
    // ProductInventoryMetricCollectorTest, which captures the registered supplier - calling the
    // method here would stay green through exactly the mis-wiring that caused the defect.
    assertEquals(
        endpointBaseline + 2L,
        collector.countVulnerableEndpoints(),
        "the count must span every tenant; a short count means it lost its TxCtx.allTenants()"
            + " scope");
  }

  @Test
  @DisplayName("the scoped import-mapper count spans every tenant, not zero")
  void importMapperCountSpansTenants() {
    assertEquals(
        mapperBaseline + 1L,
        collector.countImportMappers(),
        "import_mappers has been active since the pilot; a short count means a lost scope");
  }

  @Test
  @DisplayName("without a scope the activated table denies every row, so the red half is real")
  void unscopedCountIsZero() {
    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT count(*) FROM assets WHERE asset_type = 'Endpoint'"
                + " AND can_access_tenant(tenant_id)",
            Long.class),
        "with no scope set, can_access_tenant must deny every row; if this is non-zero the"
            + " inspector is not firing and the assertions above prove nothing");
  }

  private void seedEndpoint(String tenantId, String name) {
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        UUID.randomUUID().toString(),
        name,
        tenantId,
        name);
  }

  private void seedImportMapper(String tenantId, String name) {
    jdbc.update(
        "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column,"
            + " mapper_created_at, mapper_updated_at, tenant_id)"
            + " VALUES (CAST(? AS uuid), ?, 'A', now(), now(), ?)",
        UUID.randomUUID().toString(),
        name,
        tenantId);
  }

  private void seedFinding(String tenantId, String value) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, ?, now(), now(), 0, false, true, ?)",
        injectId,
        "telemetry-inject-" + value,
        tenantId);
    jdbc.update(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
            + " VALUES (?, 'hostname', 'text', ?, ?, now(), now(), ?)",
        UUID.randomUUID().toString(),
        value,
        injectId,
        tenantId);
  }
}
