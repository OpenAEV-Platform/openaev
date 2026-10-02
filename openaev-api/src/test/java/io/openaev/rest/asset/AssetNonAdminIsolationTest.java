package io.openaev.rest.asset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
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
 * Isolation must not depend on the caller being an administrator. {@link AssetHttpIsolationTest}
 * runs as admin, which bypasses RBAC; this one runs as a non-admin belonging to two tenants and
 * holding only the capabilities needed to read assets, and shows the scope taken from the request
 * is what isolates, never the isAdmin flag.
 *
 * <p>Both the endpoint route and the generic asset route are exercised, because they reach the same
 * table through different queries and a non-admin's capability filtering applies to each of them
 * separately.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser(isAdmin = false)
@DisplayName("assets isolation holds for a non-admin spanning two tenants")
class AssetNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_ASSETS =
      Set.of(Capability.ACCESS_ASSETS, Capability.MANAGE_ASSETS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String endpointA;
  private String endpointB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsTo() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("asset-nonadmin-a", READ_ASSETS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("asset-nonadmin-b", READ_ASSETS).getId();
    endpointA = seedEndpoint(tenantA, "nonadmin-endpoint-a");
    endpointB = seedEndpoint(tenantB, "nonadmin-endpoint-b");
  }

  @Test
  @DisplayName("a non-admin searching endpoints under tenant A's path sees A's and not B's")
  void endpointSearchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String body = searchBody("/api/tenants/{tenantId}/endpoints/search");
    assertTrue(
        body.contains(endpointA),
        "A's endpoint must be visible to a non-admin member of A: " + body);
    assertFalse(body.contains(endpointB), "B's endpoint must not be visible under A's path");
  }

  @Test
  @DisplayName("a non-admin searching the generic asset route under A sees A's row and not B's")
  void genericAssetSearchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String body = searchBody("/api/tenants/{tenantId}/assets/search");
    assertTrue(body.contains(endpointA), "A's asset must be visible on the generic route: " + body);
    assertFalse(body.contains(endpointB), "B's asset must not be visible under A's path");
  }

  @Test
  @DisplayName("a non-admin reading B's endpoint under tenant A's path gets a not found")
  void readOtherTenantEndpointIsNotFoundForNonAdmin() throws Exception {
    mvc.perform(get("/api/tenants/{tenantId}/endpoints/{id}", tenantA, endpointB))
        .andExpect(status().isNotFound());
  }

  private String searchBody(String uri) throws Exception {
    return mvc.perform(
            post(uri, tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":0,\"size\":50}")
                .with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Single INSERT, never INSERT then UPDATE: see the note on {@code AssetHttpIsolationTest}. */
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
}
