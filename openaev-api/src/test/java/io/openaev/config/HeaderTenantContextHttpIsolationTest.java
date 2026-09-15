package io.openaev.config;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves, through the real HTTP stack, that a request naming exactly one tenant in {@code
 * X-Tenant-Ids} on the regular (non-prefixed) route gets that tenant as its ambient {@link
 * io.openaev.context.TenantContext}, so the v1 mechanisms still reading it (Hibernate {@code
 * tenantFilter}, {@code TenantBaseListener}, MinIO object paths) follow the header the same way the
 * {@code /api/tenants/&#123;tenantId&#125;/...} path does.
 *
 * <p>Uses {@code vulnerabilities} and {@code documents}: v1 tables (not in active-tables) whose
 * attribution and read scoping go entirely through the ambient tenant, and whose create handlers do
 * not attribute the write from the request scope, so the behaviour tested is the interceptor's, not
 * a handler-level fix. Reads go through the search endpoint (a criteria query the {@code
 * tenantFilter} scopes), since a JPA find-by-id bypasses the filter.
 *
 * <p>Rows are seeded with a raw native INSERT carrying an explicit tenant_id, never through the
 * ambient tenant. Between two requests that resolve different v2 scopes the transaction-local scope
 * is reset ({@link #resetScope()}), because the single test transaction otherwise refuses a nested
 * method that redefines it.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A single X-Tenant-Ids id sets the ambient tenant like the tenant path")
class HeaderTenantContextHttpIsolationTest extends IntegrationTest {

  private static final String VULNERABILITIES = "/api/vulnerabilities";
  private static final String TENANT_VULNERABILITIES = "/api/tenants/{tenantId}/vulnerabilities";
  private static final String DOCUMENTS = "/api/documents";
  private static final String HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;
  private String vulnB;
  private String vulnDefault;
  private String extB;
  private String extDefault;

  @BeforeEach
  void seedTwoTenantsWithOneVulnerabilityEach() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("hdr-ctx-b").getId();
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    extB = "CVE-HDR-B-" + System.nanoTime();
    extDefault = "CVE-HDR-DEF-" + System.nanoTime();
    vulnB = seedVulnerability(tenantB, extB);
    vulnDefault = seedVulnerability(DEFAULT_TENANT, extDefault);
  }

  @Test
  @DisplayName("a v1 read on the regular route follows a single header id, not the default tenant")
  void headerReadFollowsTheSingleHeaderId() throws Exception {
    // under the B header, B's row is visible ...
    assertTrue(searchContains(tenantB, extB, vulnB), "B's row must be visible under the B header");
    // ... and the default tenant's row is not
    assertFalse(
        searchContains(tenantB, extDefault, vulnDefault),
        "the default tenant's row must not leak into a B-header read");
  }

  @Test
  @DisplayName("a v1 create on the regular route is stamped by the listener with the header tenant")
  void headerCreateLandsInTheHeaderTenant() throws Exception {
    resetScope();
    String createdId = createVulnerabilityViaHeader(tenantB, "CVE-HDR-NEW-" + System.nanoTime());
    assertEquals(
        tenantB,
        rawTenantId("vulnerabilities", "vulnerability_id", createdId),
        "a create with a single header id must be attributed to that tenant");
  }

  @Test
  @DisplayName(
      "a document upload then download on the regular route round-trips under one header id")
  void headerDocumentUploadThenDownloadRoundTrips() throws Exception {
    byte[] bytes = ("hdr-doc-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
    MockMultipartFile file =
        new MockMultipartFile("file", "hdr.txt", MediaType.TEXT_PLAIN_VALUE, bytes);
    MockMultipartFile input =
        new MockMultipartFile(
            "input", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes(StandardCharsets.UTF_8));

    resetScope();
    String body =
        mvc.perform(
                multipart(DOCUMENTS).file(file).file(input).header(HEADER, tenantB).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String documentId = JsonPath.read(body, "$.document_id");
    assertEquals(
        tenantB,
        rawTenantId("documents", "document_id", documentId),
        "the uploaded document must land in the header tenant");

    // download under the same header tenant returns the bytes end to end (MinIO path follows B)
    byte[] downloaded =
        mvc.perform(get(DOCUMENTS + "/{id}/file", documentId).header(HEADER, tenantB))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertArrayEquals(bytes, downloaded, "the download must return the uploaded bytes");
  }

  @Test
  @DisplayName("a non-member id in the header is refused with the same status as on the path")
  void headerNonMemberIsRefusedTheSameWayAsThePath() throws Exception {
    // Tenant onboarding attaches its creator, so detach the current user to get a true non-member.
    String tenantC = tenantHelper.createTenant("hdr-ctx-c").getId();
    String userId = testUserHolder.get().getId();
    tenantRepository.removeUserFromTenant(userId, tenantC);
    tenantMembershipCacheManager.evict(userId, tenantC);

    resetScope();
    int headerStatus =
        mvc.perform(get(VULNERABILITIES + "/{id}", vulnB).header(HEADER, tenantC))
            .andReturn()
            .getResponse()
            .getStatus();
    resetScope();
    int pathStatus =
        mvc.perform(get(TENANT_VULNERABILITIES + "/{id}", tenantC, vulnB))
            .andReturn()
            .getResponse()
            .getStatus();

    assertEquals(HttpStatus.FORBIDDEN.value(), headerStatus, "a non-member header id must be 403");
    assertEquals(pathStatus, headerStatus, "the header route must refuse it exactly like the path");
  }

  @Test
  @DisplayName("several ids or no header leave the ambient tenant at the default")
  void severalIdsOrNoHeaderLeaveTheAmbientTenantUnchanged() throws Exception {
    // several ids: the ambient tenant is not narrowed, so B's v1 row stays invisible ...
    assertFalse(
        searchContains(DEFAULT_TENANT + "," + tenantB, extB, vulnB),
        "several ids must not narrow the ambient tenant to B");
    // ... and the default tenant is what a no-header (tenant-unaware) client reads (#6331, #6332)
    assertTrue(
        searchContains(null, extDefault, vulnDefault),
        "a no-header client must still read the default tenant");
  }

  @Test
  @DisplayName("the ambient tenant is cleared after a header-scoped request")
  void ambientTenantIsClearedAfterAHeaderScopedRequest() throws Exception {
    // a first request scoped to B sees B ...
    assertTrue(searchContains(tenantB, extB, vulnB), "the B-scoped request must see B's row");
    // ... a following no-header request must see the default tenant again, not B
    assertFalse(
        searchContains(null, extB, vulnB),
        "the ambient tenant must be cleared, not left pinned to B");
    assertTrue(
        searchContains(null, extDefault, vulnDefault),
        "the following request must read the default tenant");
  }

  /**
   * Whether a search for {@code textSearch}, scoped by {@code header}, returns {@code expectedId}.
   */
  private boolean searchContains(String header, String textSearch, String expectedId)
      throws Exception {
    resetScope();
    var request =
        post(VULNERABILITIES + "/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(PaginationFixture.simpleTextSearch(textSearch)))
            .with(csrf());
    if (header != null) {
      request = request.header(HEADER, header);
    }
    String body =
        mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body.contains(expectedId);
  }

  private String createVulnerabilityViaHeader(String tenantId, String externalId) throws Exception {
    String body =
        mvc.perform(
                post(VULNERABILITIES)
                    .header(HEADER, tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(vulnerabilityBody(externalId))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.vulnerability_id");
  }

  private static String vulnerabilityBody(String externalId) {
    return "{\"vulnerability_external_id\":\"" + externalId + "\",\"vulnerability_cvss_v31\":5.0}";
  }

  private String seedVulnerability(String tenantId, String externalId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO vulnerabilities (vulnerability_id, vulnerability_external_id, tenant_id)"
                + " VALUES (?1, ?2, ?3)")
        .setParameter(1, id)
        .setParameter(2, externalId)
        .setParameter(3, tenantId)
        .executeUpdate();
    return id;
  }

  /**
   * Clears the transaction-local v2 scope so the next request sets it fresh. Without it the single
   * test transaction refuses a second request that resolves a different scope.
   */
  private void resetScope() {
    entityManager.flush();
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', '', true)")
        .getSingleResult();
  }

  private String rawTenantId(String table, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?")) {
                stmt.setString(1, id);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
