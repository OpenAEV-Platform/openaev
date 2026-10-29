package io.openaev.injectors.phishing.api;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.injectors.phishing.form.PhishingLandingPageInput;
import io.openaev.utils.TenantIsolationTestHelper;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * phishing_landing_pages read/write isolation through the real HTTP endpoint (v2 activation,
 * #7901), on both routes: the tenant path and the {@code X-Tenant-Ids} header. Every row is seeded
 * with a raw native INSERT carrying an explicit tenant_id, never through v1 TenantContext.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=phishing_landing_pages")
@io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
@DisplayName(
    "phishing_landing_pages read and write isolation through the real HTTP endpoint, both routes")
class PhishingLandingPageHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_PAGES = "/api/tenants/{tenantId}/phishing/landing-pages";
  private static final String PAGES = "/api/phishing/landing-pages";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String pageA;
  private String pageB;

  @BeforeEach
  void seedTwoTenantsWithOnePageEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("phishing-page-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("phishing-page-iso-b").getId();
    pageA = seedPage(tenantA, "page-a");
    pageB = seedPage(tenantB, "page-b");
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("under tenant A's path: read returns A's landing page")
  void readOwnRowUnderOwnPath() throws Exception {
    String response =
        mvc.perform(
                get(TENANT_PAGES + "/{id}", tenantA, pageA)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(pageA, JsonPath.read(response, "$.phishing_landing_page_id"));
  }

  @Test
  @DisplayName("under tenant B's path: reading tenant A's landing page is not found")
  void readCrossTenantUnderOwnPathIsNotFound() throws Exception {
    mvc.perform(
            get(TENANT_PAGES + "/{id}", tenantB, pageA)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header: reading tenant A's landing page is not found for tenant B")
  void readCrossTenantViaHeaderIsNotFound() throws Exception {
    TenantContext.clearCurrentTenant();
    mvc.perform(
            get(PAGES + "/{id}", pageA)
                .header("X-Tenant-Ids", tenantB)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: list returns A's landing page and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_PAGES, tenantA).accept(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains(pageA), "A's landing page must appear under tenant A's path");
    assertFalse(response.contains(pageB), "B's landing page must not appear under tenant A's path");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    PhishingLandingPageInput input = new PhishingLandingPageInput();
    input.setName("created-a");

    String response =
        mvc.perform(
                post(TENANT_PAGES, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.phishing_landing_page_id");
    assertEquals(tenantA, rawTenantId(createdId), "the created page must belong to tenant A");
  }

  @Test
  @DisplayName("a create via the X-Tenant-Ids header is attributed to the header tenant")
  void createViaHeaderIsAttributedToHeaderTenant() throws Exception {
    TenantContext.clearCurrentTenant();
    PhishingLandingPageInput input = new PhishingLandingPageInput();
    input.setName("created-header-b");

    String response =
        mvc.perform(
                post(PAGES)
                    .header("X-Tenant-Ids", tenantB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.phishing_landing_page_id");
    assertEquals(tenantB, rawTenantId(createdId), "the created page must belong to tenant B");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    TenantContext.clearCurrentTenant();
    PhishingLandingPageInput input = new PhishingLandingPageInput();
    input.setName("no-selector");

    mvc.perform(
            post(PAGES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant B's path: updating tenant A's landing page is not found")
  void updateCrossTenantIsRejected() throws Exception {
    PhishingLandingPageInput input = new PhishingLandingPageInput();
    input.setName("hijacked");

    mvc.perform(
            put(TENANT_PAGES + "/{id}", tenantB, pageA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "under tenant B's path: deleting tenant A's landing page is not found and leaves it in"
          + " place")
  void deleteCrossTenantIsRejected() throws Exception {
    mvc.perform(delete(TENANT_PAGES + "/{id}", tenantB, pageA).with(csrf()))
        .andExpect(status().isNotFound());

    entityManager.flush();
    entityManager.clear();
    assertEquals(
        1L, rawCount(pageA), "tenant A's landing page must not have been deleted cross-tenant");
  }

  private String rawTenantId(String pageId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM phishing_landing_pages WHERE"
                          + " phishing_landing_page_id = ?")) {
                stmt.setString(1, pageId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String pageId) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT count(*) FROM phishing_landing_pages WHERE"
                          + " phishing_landing_page_id = ?")) {
                stmt.setString(1, pageId);
                try (var rows = stmt.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private String seedPage(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO phishing_landing_pages (phishing_landing_page_id,"
                + " phishing_landing_page_name, phishing_landing_page_capture_submitted_data,"
                + " phishing_landing_page_capture_passwords, phishing_landing_page_created_at,"
                + " phishing_landing_page_updated_at, tenant_id) VALUES (?1, ?2, true, true, now(),"
                + " now(), ?3)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, tenantId)
        .executeUpdate();
    return id;
  }
}
