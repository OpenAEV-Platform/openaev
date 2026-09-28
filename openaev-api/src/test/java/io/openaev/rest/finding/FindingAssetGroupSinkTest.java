package io.openaev.rest.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code Finding.getAssetGroups()} is a computed {@code @JsonProperty} that walks {@code
 * inject.getAssetGroups()}, a lazy collection on the v2-active {@code asset_groups} table. Jackson
 * resolves it AFTER the controller's {@code @Transactional} method returns, through open-in-view,
 * by which point the transaction's tenant scope is gone.
 *
 * <p>That is the #7026 shape: the endpoint keeps returning 200 with an empty array rather than
 * failing, and the caller cannot tell "this finding has no asset group" from "the scope was lost".
 * Carrying a {@code TxCtx} is necessary but not sufficient; the association has to be resolved
 * inside the scoped transaction.
 *
 * <p>The assertion is NON-EMPTY on purpose. An empty-array assertion is exactly what the regression
 * satisfies.
 *
 * <p>The class is deliberately NOT {@code @Transactional}. An earlier version was, and it proved
 * less than it claimed: with the test holding the transaction open, the controller joins it and
 * Jackson serializes inside it, where the scope still exists. Open-in-view is only exercised when
 * the request's own transaction has closed, which means committing the seed and sweeping it by
 * hand.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=findings,asset_groups,assets")
@WithMockUser(isAdmin = true)
@DisplayName("a finding still carries its inject's asset groups when serialized")
class FindingAssetGroupSinkTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private FindingService findingService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  @Autowired private javax.sql.DataSource dataSource;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private org.springframework.jdbc.core.JdbcTemplate jdbc;
  private String tenantId;
  private String findingId;
  private String assetGroupId;
  private String assetId;

  @org.junit.jupiter.api.AfterEach
  void sweep() {
    jdbc.update("DELETE FROM findings_assets WHERE asset_id = ?", assetId);
    jdbc.update("DELETE FROM asset_groups_assets WHERE asset_id = ?", assetId);
    jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injects_asset_groups WHERE asset_group_id = ?", assetGroupId);
    jdbc.update("DELETE FROM asset_groups WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    // The tenant was COMMITTED by createTenantWithCurrentUser, and this class is not transactional,
    // so nothing rolls it back. Its own rows go first, as deleteCommittedTenants documents, then
    // the
    // tenant itself. Leaving it behind is what accumulates onboarding residue and per-tenant broker
    // queues across a run (#7873).
    tenantHelper.deleteCommittedTenants(tenantId);
    // switchToTenant set the thread-local; it is not transactional either and would otherwise leak
    // into whatever test runs next on this thread.
    TenantContext.clearCurrentTenant();
  }

  @BeforeEach
  void seedAFindingOnAnInjectThatTargetsAnAssetGroup() {
    jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    // The seed needs its own short transaction and has to COMMIT, so that the request under test
    // runs against committed rows and its own transaction is the one that closes before Jackson
    // serializes.
    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              try {
                tenantId =
                    tenantHelper.createTenantWithCurrentUser("fag-" + UUID.randomUUID()).getId();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
              tenantHelper.switchToTenant(tenantId, entityManager);

              Inject inject = InjectFixture.getDefaultInject();
              inject.setTenant(new Tenant(tenantId));
              entityManager.persist(inject);
              entityManager.flush();

              assetGroupId = UUID.randomUUID().toString();
              entityManager
                  .createNativeQuery(
                      "INSERT INTO asset_groups (asset_group_id, asset_group_name,"
                          + " asset_group_dynamic_filter, asset_group_created_at,"
                          + " asset_group_updated_at, tenant_id)"
                          + " VALUES (:id, :name, CAST('{}' AS json), now(), now(), :tenantId)")
                  .setParameter("id", assetGroupId)
                  .setParameter("name", "sink-group")
                  .setParameter("tenantId", tenantId)
                  .executeUpdate();
              entityManager
                  .createNativeQuery(
                      "INSERT INTO injects_asset_groups (inject_id, asset_group_id)"
                          + " VALUES (:i, :g)")
                  .setParameter("i", inject.getId())
                  .setParameter("g", assetGroupId)
                  .executeUpdate();

              Finding finding = new Finding();
              finding.setValue("sink-" + UUID.randomUUID());
              finding.setType(ContractOutputType.Text);
              finding.setField("hostname");
              finding.setName("sink probe");
              findingId =
                  findingService
                      .createFinding(TxCtx.forTenant(tenantId), finding, inject.getId())
                      .getId();
              entityManager.flush();

              // A linked asset too: finding_assets is a lazy @ManyToMany on the assets table, the
              // other association this endpoint serializes after its transaction has closed.
              assetId = UUID.randomUUID().toString();
              entityManager
                  .createNativeQuery(
                      "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
                          + " asset_updated_at, tenant_id, asset_hostname, endpoint_platform,"
                          + " endpoint_arch) VALUES (:id, 'sink-asset', 'Endpoint', now(), now(),"
                          + " :tenantId, 'sink-asset', 'Linux', 'x86_64')")
                  .setParameter("id", assetId)
                  .setParameter("tenantId", tenantId)
                  .executeUpdate();
              entityManager
                  .createNativeQuery(
                      "INSERT INTO findings_assets (finding_id, asset_id) VALUES (:f, :a)")
                  .setParameter("f", findingId)
                  .setParameter("a", assetId)
                  .executeUpdate();
              // The same asset inside the asset group, which is a second, deeper association on the
              // same activated table: each group serializes its own assets as ids through
              // MultiIdListSerializer, one level below the group list this test already covers.
              // Without this row the nested array is legitimately empty and proves nothing.
              entityManager
                  .createNativeQuery(
                      "INSERT INTO asset_groups_assets (asset_group_id, asset_id)"
                          + " VALUES (:g, :a)")
                  .setParameter("g", assetGroupId)
                  .setParameter("a", assetId)
                  .executeUpdate();
              entityManager.flush();
            });
  }

  @Test
  @DisplayName("GET a finding: finding_asset_groups is not silently empty")
  void findingCarriesItsAssetGroups() throws Exception {
    String body =
        mvc.perform(get("/api/tenants/{tenantId}/findings/{id}", tenantId, findingId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Asserted on the exact JSON paths, not on the whole body. The first version of this test used
    // body.contains(assetId), which passed on the copy of that id inside finding_assets and so said
    // nothing about the asset ids nested one level down, inside each asset group. The fail-closed
    // detector caught what the assertion missed.
    DocumentContext json = JsonPath.parse(body);

    assertEquals(
        List.of(assetGroupId),
        json.read("$.finding_asset_groups[*].asset_group_id"),
        "finding_asset_groups must still hold the inject's asset group; an empty array here is the"
            + " #7026 regression, not a data problem: "
            + body);
    assertEquals(
        List.of(assetId),
        json.read("$.finding_assets"),
        "finding_assets must still hold the linked asset, for the same reason and on the other"
            + " activated table: "
            + body);
    // The third assertion of #7856 checked $.finding_asset_groups[0].asset_group_assets, the asset
    // ids nested one level below. That level no longer exists in this payload: the endpoint returns
    // FindingOutput, whose asset groups are AssetGroupSimple - id and name - which is what
    // AggregatedFindingOutput already returned to the findings list, and all the frontend reads.
    // The nested array is therefore not empty but absent, and its fail-closed detector has nothing
    // left to detect. The two assertions above keep the guard where it still applies, and they
    // remain meaningful: the mapper reads both collections, so a scope regression would empty them
    // exactly as before.
    assertFalse(
        json.read("$.finding_asset_groups[0]", java.util.Map.class)
            .containsKey("asset_group_assets"),
        "the finding payload must not carry the nested asset ids: the list and the detail endpoints"
            + " agree on AssetGroupSimple, and a divergence here would be a contract regression: "
            + body);
  }
}
