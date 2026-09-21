package io.openaev.telemetry.metric_collectors;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The asset inventory gauge is deliberately platform-wide: telemetry is per-instance, not
 * per-tenant. Under v1 that came for free, because the supplier runs on the OpenTelemetry exporter
 * thread outside any {@code @Transactional} method and the Hibernate {@code tenantFilter} is only
 * enabled on a transactional call.
 *
 * <p>Activating {@code assets} falsifies that reasoning. {@code TenantStatementInspector} does not
 * care whether a transaction is active: with {@code app.current_tenants} unset, {@code
 * can_access_tenant} returns false for every row and the gauge reports an empty map. It keeps
 * reporting, it just reports nothing, which is worse than failing.
 *
 * <p>The class is not {@code @Transactional}: the scoped primitive refuses to open inside an active
 * transaction, so seeding goes through an auto-committing {@link JdbcTemplate}, which also bypasses
 * the inspector and therefore sees every row whatever the scope.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@DisplayName("the asset inventory gauge keeps counting across tenants once assets is v2-active")
class AssetMetricTenantScopeTest extends IntegrationTest {

  /**
   * Built here, never injected. {@code NoOpOpenTelemetryConfig} replaces the {@code
   * AssetMetricCollector} bean with a Mockito mock in every integration context, so an
   * {@code @Autowired} collector returns Mockito's default empty map and the test would measure the
   * mock rather than the gauge. This cost a long investigation once; the comment is the fix for the
   * next reader.
   */
  private AssetMetricCollector collector;

  @Autowired private ProductInventoryMetricCollector inventoryCollector;
  @Autowired private DataSource dataSource;
  @Autowired private MetricRegistry metricRegistry;
  @Autowired private TenantScopedTransaction tenantTx;
  @PersistenceContext private EntityManager em;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private long baseline;
  private long endpointBaseline;
  private long platformBaseline;

  @BeforeEach
  void seedTwoTenantsWithOneAssetEach() {
    jdbc = new JdbcTemplate(dataSource);
    collector = new AssetMetricCollector(metricRegistry, tenantTx);
    // @PersistenceContext is container-injected in production; wire the real one here.
    ReflectionTestUtils.setField(collector, "entityManager", em);
    // A delta, never an absolute count: the test database is shared, and this test must never
    // delete rows it did not create.
    baseline = requireNonNull(jdbc.queryForObject("SELECT count(*) FROM assets", Long.class));
    endpointBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM assets WHERE asset_type = 'Endpoint'", Long.class));
    platformBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM assets WHERE asset_type = 'SecurityPlatform'", Long.class));
    seedAsset(seedTenant("asset-telemetry-a-" + UUID.randomUUID()), "telemetry-endpoint-a");
    seedAsset(seedTenant("asset-telemetry-b-" + UUID.randomUUID()), "telemetry-endpoint-b");
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("the asset gauge counts every tenant's rows, not zero")
  void assetGaugeCountsAcrossTenants() {
    // Non-empty on purpose: asserting zero would stay green through exactly the fail-closed
    // regression this pins. Two tenants, one asset each, one platform-wide total.
    long rawNow = requireNonNull(jdbc.queryForObject("SELECT count(*) FROM assets", Long.class));
    long counted = collector.collectAssets().values().stream().mapToLong(Long::longValue).sum();
    assertEquals(
        rawNow,
        counted,
        "raw rows visible to JDBC: "
            + rawNow
            + ", counted by the gauge: "
            + counted
            + ". The gauge must span every tenant; a short count means the supplier lost its"
            + " TxCtx.allTenants() scope and silently stopped counting");
  }

  @Test
  @DisplayName("the same query without a scope returns nothing: this is what the scope prevents")
  @SuppressWarnings("unchecked")
  void theSameQueryUnscopedSeesNothing() {
    // The red half of the pair. Without it, the two green assertions above would also pass on a
    // build where the inspector never fires, and would prove nothing about scoping. Same SQL as
    // the gauge, run outside any TxCtx: fail-closed means zero rows, silently.
    List<Object[]> rows =
        em.createNativeQuery(
                "select t.asset_category, t.has_agent, count(*) from ("
                    + "  select a.asset_category,"
                    + "    exists (select 1 from agents ag where ag.agent_asset = a.asset_id) as"
                    + " has_agent"
                    + "  from assets a"
                    + ") t group by 1, 2")
            .getResultList();
    assertEquals(
        0,
        rows.size(),
        "an unscoped read of an active table must return nothing; if this now returns rows, the"
            + " inspector stopped firing and the two assertions above no longer prove anything");
  }

  @Test
  @DisplayName("the endpoint and platform JPQL, run unscoped, also sees nothing")
  void theJpqlGaugesAreAlsoFailClosedWithoutAScope() {
    // The red half for the two JPQL gauges. The native-SQL gauge has its own above; without this
    // one, their green assertions would pass on a build where the inspector never fires and would
    // prove nothing about the scope those two now open.
    assertEquals(
        0,
        em.createQuery("select e.platform, count(e) from Endpoint e group by e.platform")
            .getResultList()
            .size(),
        "an unscoped endpoint count must return nothing once assets is active");
    assertEquals(
        0,
        em.createQuery(
                "select sp.securityPlatformType, count(sp) from SecurityPlatform sp"
                    + " group by sp.securityPlatformType")
            .getResultList()
            .size(),
        "an unscoped security platform count must return nothing once assets is active");
  }

  @Test
  @DisplayName("the endpoint and security platform gauges count across tenants too")
  void endpointAndPlatformGaugesCountAcrossTenants() {
    // Both count rows of the same assets table through different queries, so each needs its own
    // scope and each is asserted separately. Delta again, never an absolute.
    long endpoints =
        inventoryCollector.collectEndpoints().values().stream().mapToLong(Long::longValue).sum();
    assertEquals(
        endpointBaseline + 2,
        endpoints,
        "endpoints_total must span every tenant once assets is v2-active");
    assertEquals(
        platformBaseline,
        inventoryCollector.collectSecurityPlatforms().values().stream()
            .mapToLong(Long::longValue)
            .sum(),
        "security_platforms_total must still see the platforms it saw before, not zero");
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

  private void seedAsset(String tenantId, String name) {
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        UUID.randomUUID().toString(),
        name,
        tenantId,
        name);
  }
}
