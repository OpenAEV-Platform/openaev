package io.openaev.rest.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code assets} activated, the tenant scope carried by the URL path
 * isolates the table through the real endpoints.
 *
 * <p>The table is {@code SINGLE_TABLE} with three discriminators, so isolation has to be proved for
 * all three: {@code Endpoint}, {@code SecurityPlatform} and the bare {@code Asset} an AI target is.
 * They are served by three different controllers plus a generic {@code /api/assets} surface that
 * reads across all of them, and a leak on any one of those is a leak on the table.
 *
 * <p>Two rules inherited from the asset-group isolation tests, both learned the hard way. Every
 * test method stays on ONE tenant path, because {@code TenantScopeTransactionAspect}'s nesting
 * guard refuses a second request that redefines the transaction scope. And every positive assertion
 * is non-empty: an assertion that a result is empty stays green through exactly the fail-closed
 * regression being guarded against, so it proves nothing.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser(isAdmin = true)
@DisplayName("assets read isolation through the real HTTP endpoints, all three discriminators")
class AssetHttpIsolationTest extends IntegrationTest {

  private static final String ENDPOINT_BY_ID = "/api/tenants/{tenantId}/endpoints/{endpointId}";
  private static final String ASSET_BY_ID = "/api/tenants/{tenantId}/assets/{assetId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String tenantB;
  private String endpointA;
  private String endpointB;
  private String platformA;
  private String platformB;
  private String aiTargetA;
  private String aiTargetB;

  @BeforeEach
  void seedTwoTenantsWithOneRowOfEachDiscriminator() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("asset-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("asset-http-iso-b").getId();
    endpointA = seedEndpoint(tenantA, "iso-endpoint-a");
    endpointB = seedEndpoint(tenantB, "iso-endpoint-b");
    platformA = seedSecurityPlatform(tenantA, "iso-platform-a");
    platformB = seedSecurityPlatform(tenantB, "iso-platform-b");
    aiTargetA = seedAiTarget(tenantA, "iso-ai-a");
    aiTargetB = seedAiTarget(tenantB, "iso-ai-b");
  }

  // -- Endpoint ------------------------------------------------------------

  @Test
  @DisplayName("under tenant A's path: A's endpoint is readable and carries its real name")
  void readOwnEndpointUnderOwnPath() throws Exception {
    String body = okBody(get(ENDPOINT_BY_ID, tenantA, endpointA));
    assertTrue(
        body.contains("iso-endpoint-a"), "A's endpoint must come back with its name: " + body);
  }

  @Test
  @DisplayName("under tenant A's path: B's endpoint is not found")
  void readOtherTenantEndpointIsNotFound() throws Exception {
    mvc.perform(get(ENDPOINT_BY_ID, tenantA, endpointB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: the isolation is symmetric")
  void readIsSymmetricUnderTenantB() throws Exception {
    mvc.perform(get(ENDPOINT_BY_ID, tenantB, endpointB)).andExpect(status().isOk());
    mvc.perform(get(ENDPOINT_BY_ID, tenantB, endpointA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: the endpoint search returns A's endpoint and not B's")
  void endpointSearchUnderTenantAReturnsOnlyA() throws Exception {
    String body = okBody(searchUnder("/api/tenants/{tenantId}/endpoints/search", tenantA));
    assertTrue(body.contains("iso-endpoint-a"), "A's endpoint must appear: " + body);
    assertFalse(body.contains("iso-endpoint-b"), "B's endpoint must not appear: " + body);
  }

  // -- SecurityPlatform ----------------------------------------------------

  @Test
  @DisplayName("under tenant A's path: the security platform list holds A's platform only")
  void securityPlatformListUnderTenantAReturnsOnlyA() throws Exception {
    String body = okBody(get("/api/tenants/{tenantId}/security_platforms", tenantA));
    assertTrue(body.contains("iso-platform-a"), "A's platform must appear: " + body);
    assertFalse(body.contains("iso-platform-b"), "B's platform must not appear: " + body);
  }

  @Test
  @DisplayName("under tenant A's path: the security platform search returns A's platform only")
  void securityPlatformSearchUnderTenantAReturnsOnlyA() throws Exception {
    String body = okBody(searchUnder("/api/tenants/{tenantId}/security_platforms/search", tenantA));
    assertTrue(body.contains("iso-platform-a"), "A's platform must appear: " + body);
    assertFalse(body.contains("iso-platform-b"), "B's platform must not appear: " + body);
  }

  // -- AI target, the bare Asset discriminator -----------------------------

  @Test
  @DisplayName("under tenant A's path: the AI target list holds A's target only")
  void aiTargetListUnderTenantAReturnsOnlyA() throws Exception {
    String body = okBody(get("/api/tenants/{tenantId}/ai_targets", tenantA));
    assertTrue(body.contains("iso-ai-a"), "A's AI target must appear: " + body);
    assertFalse(body.contains("iso-ai-b"), "B's AI target must not appear: " + body);
  }

  // -- The generic /api/assets surface, which reads across discriminators ---

  @Test
  @DisplayName("under tenant A's path: the generic asset search spans A's discriminators only")
  void genericAssetSearchUnderTenantAReturnsOnlyA() throws Exception {
    String body = okBody(searchUnder("/api/tenants/{tenantId}/assets/search", tenantA));
    // Two discriminators, each with its own positive half. A negative alone cannot tell isolation
    // apart from a route that returns nothing, and there is no assertion here about security
    // platforms on purpose: AssetService.searchAssets excludes SECURITY_PLATFORM unconditionally,
    // so asserting B's platform is absent would hold with tenant isolation entirely removed.
    assertTrue(body.contains("iso-endpoint-a"), "A's endpoint must appear: " + body);
    assertTrue(body.contains("iso-ai-a"), "A's AI target must appear: " + body);
    assertFalse(body.contains("iso-endpoint-b"), "B's endpoint must not appear: " + body);
    assertFalse(body.contains("iso-ai-b"), "B's AI target must not appear: " + body);
  }

  @Test
  @DisplayName("under tenant A's path: B's asset is not found on the generic route either")
  void genericAssetByIdIsIsolated() throws Exception {
    mvc.perform(get(ASSET_BY_ID, tenantA, aiTargetB)).andExpect(status().isNotFound());
  }

  // -- Write reach ---------------------------------------------------------

  @Test
  @DisplayName("under tenant A's path: deleting B's endpoint does not remove it")
  void deleteOtherTenantEndpointLeavesItInPlace() throws Exception {
    mvc.perform(delete(ENDPOINT_BY_ID, tenantA, endpointB).with(csrf()));
    // The status is not the assertion: what matters is that the row is still there. A delete that
    // silently matched zero rows would return 200 and still be correct, so the ground truth is read
    // through raw JDBC, outside the request scope.
    assertEquals(
        "iso-endpoint-b", rawName(endpointB), "B's endpoint must survive a delete issued under A");
  }

  // -- helpers -------------------------------------------------------------

  private org.springframework.test.web.servlet.RequestBuilder searchUnder(
      String uri, String tenant) {
    return post(uri, tenant)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"page\":0,\"size\":50}")
        .with(csrf());
  }

  private String okBody(org.springframework.test.web.servlet.RequestBuilder request)
      throws Exception {
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String seedEndpoint(String tenantId, String name) {
    return seedAsset(
        tenantId,
        name,
        "Endpoint",
        ", asset_hostname, endpoint_platform, endpoint_arch",
        ", :name, 'Linux', 'x86_64'");
  }

  private String seedSecurityPlatform(String tenantId, String name) {
    return seedAsset(tenantId, name, "SecurityPlatform", ", security_platform_type", ", 'EDR'");
  }

  /** An AI target is a bare Asset row with the AI category, not its own discriminator. */
  private String seedAiTarget(String tenantId, String name) {
    return seedAsset(tenantId, name, "Asset", ", asset_category", ", 'AI_TARGET'");
  }

  /**
   * One INSERT per row, never an INSERT followed by an UPDATE. An UPDATE on an activated table goes
   * through the statement inspector, which evaluates it against the transaction scope; the fixture
   * runs before any request has set one, so the UPDATE matches zero rows and reports success. The
   * first version of this class seeded that way, and the endpoint and platform tests still passed
   * because they assert on the name the INSERT had already written. Only the AI target test, whose
   * subject was the very column the UPDATE was meant to set, exposed it.
   */
  private String seedAsset(
      String tenantId, String name, String discriminator, String extraColumns, String extraValues) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
                + " asset_updated_at, tenant_id"
                + extraColumns
                + ") VALUES (:id, :name, :type, now(), now(), :tenantId"
                + extraValues
                + ")")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("type", discriminator)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    return id;
  }

  /**
   * Ground truth, unaffected by the request scope: the seed and the assertion share this session.
   */
  private String rawName(String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement st =
                  connection.prepareStatement("SELECT asset_name FROM assets WHERE asset_id = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }
}
