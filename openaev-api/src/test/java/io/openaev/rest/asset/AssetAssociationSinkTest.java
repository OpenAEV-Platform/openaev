package io.openaev.rest.asset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The #7026 shape, in the direction this activation creates.
 *
 * <p>That bug shipped because a lazy association onto a freshly activated table was serialized by
 * {@code MultiIdListSerializer} AFTER the controller's {@code @Transactional} method returned
 * (open-in-view), by which point the transaction's tenant scope was gone. The inspector then
 * fail-closed the lazy query and the endpoint returned 200 with an empty array, so nothing failed
 * loudly and CI never saw it either, because the test profile ships an empty {@code active-tables}.
 *
 * <p>Activating {@code assets} recreates that exposure on twelve endpoints that return a raw entity
 * carrying a lazy association onto the table. Carrying a {@code TxCtx} is necessary but NOT
 * sufficient: the association has to be resolved inside the scoped transaction. This pins the
 * families, with the activation switched on the way production has it, and asserts the association
 * is NON-EMPTY, because an empty-array assertion is exactly what the regression satisfies.
 *
 * <p>The class is deliberately NOT {@code @Transactional}, and an earlier version of it was, which
 * made it prove nothing. With the test holding the transaction open, the controller joins it and
 * Jackson serializes inside it, where the scope still exists; open-in-view is only exercised once
 * the request's own transaction has closed. That means committing the seed and sweeping it by hand.
 * The same mistake, caught on FindingApi, turned a passing test into a real defect once corrected.
 *
 * <p>{@code AssetGroupApi} is pinned first on purpose: it belongs to the previous activation and
 * nothing in it changes here, yet {@code AssetGroup.assets} points at the table being activated
 * now, so this lot can break the previous lot's endpoint without touching a line of its code.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=asset_groups,assets")
@WithMockUser(isAdmin = true)
@DisplayName("lazy associations onto assets still resolve when the endpoint serializes them")
class AssetAssociationSinkTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private javax.sql.DataSource dataSource;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String assetId;
  private String groupId;

  @org.junit.jupiter.api.AfterEach
  void sweep() {
    jdbc.update("DELETE FROM asset_groups_assets WHERE asset_id = ?", assetId);
    jdbc.update("DELETE FROM asset_groups WHERE tenant_id = ?", tenantA);
    jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantA);
  }

  @BeforeEach
  void seedAGroupHoldingAnAsset() {
    jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    // Its own short transaction, committed: the request under test must run against committed rows
    // so that its transaction is the one closing before Jackson serializes.
    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              try {
                tenantA =
                    tenantHelper.createTenantWithCurrentUser("sink-" + UUID.randomUUID()).getId();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
              assetId = seedEndpoint(tenantA, "sink-endpoint");
              groupId = seedAssetGroup(tenantA, "sink-group");
              entityManager
                  .createNativeQuery(
                      "INSERT INTO asset_groups_assets (asset_group_id, asset_id)"
                          + " VALUES (:g, :a)")
                  .setParameter("g", groupId)
                  .setParameter("a", assetId)
                  .executeUpdate();
              entityManager.flush();
            });
  }

  @Test
  @DisplayName("GET an asset group: its assets array survives open-in-view serialization")
  void assetGroupStillCarriesItsAssets() throws Exception {
    String body =
        mvc.perform(get("/api/tenants/{tenantId}/asset_groups/{id}", tenantA, groupId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        body.contains(assetId),
        "asset_group_assets must still hold the asset once assets is active; an empty array here"
            + " is the #7026 regression, not a data problem: "
            + body);
  }

  private String seedEndpoint(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
                + " asset_updated_at, tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
                + " VALUES (:id, :name, 'Endpoint', now(), now(), :tenantId, :name, 'Linux',"
                + " 'x86_64')")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedAssetGroup(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO asset_groups (asset_group_id, asset_group_name,"
                + " asset_group_dynamic_filter, asset_group_created_at, asset_group_updated_at,"
                + " tenant_id) VALUES (:id, :name, CAST('{}' AS json), now(), now(), :tenantId)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    return id;
  }
}
