package io.openaev.rest.asset_group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.engine.model.assetgroup.AssetGroupHandler;
import io.openaev.engine.model.assetgroup.EsAssetGroup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins the tenant scope of the only background reader of {@code asset_groups}: the Elasticsearch
 * indexing cursor.
 *
 * <p>{@code asset_groups} is the first v2-activated table with an engine indexing handler, so this
 * path has no precedent among the tables already active. {@link AssetGroupHandler#fetch} runs
 * {@code AssetGroupRepository.findForIndexing}, driven by {@code EngineSyncExecutionJob}, which
 * opens {@code tenantTx.execute(TxCtx.allTenants(), ...)}. That scope is load-bearing and
 * invisible: {@code can_access_tenant} returns false when {@code app.current_tenants} is unset, so
 * an indexing sweep that lost its scope would index ZERO asset groups, with no error, no failing
 * test and no log line. The search index would simply stop being fed.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the primitive refuses to open a
 * transaction inside an active one, so seeding and cleanup go through an auto-committing {@link
 * JdbcTemplate}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=asset_groups")
@DisplayName("asset_groups background (ES indexing) tenant scope")
class AssetGroupBackgroundIsolationTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private AssetGroupHandler assetGroupHandler;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private String tenantA;
  private String tenantB;
  private String groupA;
  private String groupB;

  @BeforeEach
  void seedTwoTenantsWithOneAssetGroupEach() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("bg-iso-a-" + UUID.randomUUID());
    tenantB = seedTenant("bg-iso-b-" + UUID.randomUUID());
    groupA = seedAssetGroup(tenantA, "bg-group-a");
    groupB = seedAssetGroup(tenantB, "bg-group-b");
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM asset_groups WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("with no scope at all the indexing cursor reads ZERO rows, silently")
  void withoutAnyScopeTheIndexingCursorIsFailClosed() {
    // The degradation this whole test class exists to prevent. No exception, no empty-ish result:
    // a clean, silent zero. This is also what makes the two assertions below non-vacuous.
    List<EsAssetGroup> fetched = assetGroupHandler.fetch(Instant.EPOCH, 100);

    assertTrue(
        idsOf(fetched).stream().noneMatch(id -> id.equals(groupA) || id.equals(groupB)),
        "an unscoped indexing sweep must see neither tenant's asset group, and it must fail"
            + " closed rather than leak: "
            + idsOf(fetched));
  }

  @Test
  @DisplayName("under allTenants() the indexing cursor sees BOTH tenants' asset groups")
  void underAllTenantsTheIndexingCursorSeesEveryTenant() {
    List<String> ids =
        tenantTx.execute(
            TxCtx.allTenants(), () -> idsOf(assetGroupHandler.fetch(Instant.EPOCH, 500)));

    // Non-empty on purpose. An assertion that the result is empty would stay green through exactly
    // the fail-closed regression being guarded against.
    assertTrue(ids.contains(groupA), "tenant A's asset group must be indexed: " + ids);
    assertTrue(ids.contains(groupB), "tenant B's asset group must be indexed: " + ids);
  }

  @Test
  @DisplayName("under forTenant(A) the indexing cursor sees A's asset group and not B's")
  void underSingleTenantScopeTheIndexingCursorIsIsolated() {
    List<String> ids =
        tenantTx.execute(
            TxCtx.forTenant(tenantA), () -> idsOf(assetGroupHandler.fetch(Instant.EPOCH, 500)));

    assertTrue(ids.contains(groupA), "tenant A's asset group must be visible under A: " + ids);
    assertFalse(ids.contains(groupB), "tenant B's asset group must not be visible under A: " + ids);
  }

  @Test
  @DisplayName("the indexed document carries the owning tenant, so the index stays attributable")
  void theIndexedDocumentCarriesItsOwnTenant() {
    List<EsAssetGroup> fetched =
        tenantTx.execute(
            TxCtx.allTenants(),
            () -> assetGroupHandler.fetch(Instant.EPOCH, 500).stream().toList());

    EsAssetGroup a =
        fetched.stream()
            .filter(doc -> groupA.equals(doc.getBase_id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("tenant A's asset group was not indexed"));
    assertEquals(tenantA, a.getBase_tenant_side(), "the indexed document must carry A's tenant");
    assertEquals("bg-group-a", a.getBase_representative());
  }

  private static List<String> idsOf(List<EsAssetGroup> docs) {
    return docs.stream().map(EsAssetGroup::getBase_id).collect(Collectors.toList());
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

  private String seedAssetGroup(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO asset_groups (asset_group_id, asset_group_name, asset_group_dynamic_filter,"
            + " asset_group_created_at, asset_group_updated_at, tenant_id)"
            + " VALUES (?, ?, CAST(? AS json), now(), now(), ?)",
        id,
        name,
        "{}",
        tenantId);
    return id;
  }
}
