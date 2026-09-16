package io.openaev.rest.asset_group;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolation must not depend on the caller being an administrator. {@link
 * AssetGroupHttpIsolationTest} runs as admin, which bypasses RBAC; this one runs as a non-admin
 * belonging to two tenants and holding only the capabilities needed to read asset groups, and shows
 * the scope taken from the request is what isolates, never the isAdmin flag.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=asset_groups")
@WithMockUser(isAdmin = false)
@DisplayName("asset_groups isolation holds for a non-admin spanning two tenants")
class AssetGroupNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_ASSETS =
      Set.of(Capability.ACCESS_ASSETS, Capability.MANAGE_ASSETS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String groupA;
  private String groupB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsTo() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("ag-nonadmin-a", READ_ASSETS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("ag-nonadmin-b", READ_ASSETS).getId();
    groupA = seedAssetGroup(tenantA, "nonadmin-group-a");
    groupB = seedAssetGroup(tenantB, "nonadmin-group-b");
  }

  @Test
  @DisplayName("a non-admin searching under tenant A's path sees A's group and not B's")
  void searchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String body =
        mvc.perform(
                post("/api/tenants/{tenantId}/asset_groups/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        asJsonString(
                            PaginationFixture.getDefault().textSearch("nonadmin-group").build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        body.contains(groupA), "A's group must be visible to a non-admin member of A: " + body);
    assertFalse(body.contains(groupB), "B's group must not be visible under A's path");
  }

  @Test
  @DisplayName("a non-admin reading B's group under tenant A's path gets a not found")
  void readOtherTenantGroupIsNotFoundForNonAdmin() throws Exception {
    mvc.perform(get("/api/tenants/{tenantId}/asset_groups/{id}", tenantA, groupB))
        .andExpect(status().isNotFound());
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
