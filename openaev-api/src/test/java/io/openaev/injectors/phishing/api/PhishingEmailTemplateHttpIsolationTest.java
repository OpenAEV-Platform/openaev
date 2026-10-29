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
import io.openaev.injectors.phishing.form.PhishingEmailTemplateInput;
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
 * phishing_email_templates read/write isolation through the real HTTP endpoint (v2 activation,
 * #7901), on both routes: the tenant path and the {@code X-Tenant-Ids} header. Every row is seeded
 * with a raw native INSERT carrying an explicit tenant_id, never through v1 TenantContext.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=phishing_email_templates")
@io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
@DisplayName(
    "phishing_email_templates read and write isolation through the real HTTP endpoint, both"
        + " routes")
class PhishingEmailTemplateHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_TEMPLATES = "/api/tenants/{tenantId}/phishing/email-templates";
  private static final String TEMPLATES = "/api/phishing/email-templates";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String templateA;
  private String templateB;

  @BeforeEach
  void seedTwoTenantsWithOneTemplateEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("phishing-template-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("phishing-template-iso-b").getId();
    templateA = seedTemplate(tenantA, "template-a");
    templateB = seedTemplate(tenantB, "template-b");
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("under tenant A's path: read returns A's template")
  void readOwnRowUnderOwnPath() throws Exception {
    String response =
        mvc.perform(
                get(TENANT_TEMPLATES + "/{id}", tenantA, templateA)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(templateA, JsonPath.read(response, "$.phishing_email_template_id"));
  }

  @Test
  @DisplayName("under tenant B's path: reading tenant A's template is not found")
  void readCrossTenantUnderOwnPathIsNotFound() throws Exception {
    mvc.perform(
            get(TENANT_TEMPLATES + "/{id}", tenantB, templateA)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: reading tenant A's template is not found for tenant B")
  void readCrossTenantViaHeaderIsNotFound() throws Exception {
    TenantContext.clearCurrentTenant();
    mvc.perform(
            get(TEMPLATES + "/{id}", templateA)
                .header("X-Tenant-Ids", tenantB)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: list returns A's template and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_TEMPLATES, tenantA).accept(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains(templateA), "A's template must appear under tenant A's path");
    assertFalse(response.contains(templateB), "B's template must not appear under tenant A's path");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    PhishingEmailTemplateInput input = new PhishingEmailTemplateInput();
    input.setName("created-a");
    input.setSubject("subject-a");

    String response =
        mvc.perform(
                post(TENANT_TEMPLATES, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.phishing_email_template_id");
    assertEquals(tenantA, rawTenantId(createdId), "the created template must belong to tenant A");
  }

  @Test
  @DisplayName("a create via the X-Tenant-Ids header is attributed to the header tenant")
  void createViaHeaderIsAttributedToHeaderTenant() throws Exception {
    TenantContext.clearCurrentTenant();
    PhishingEmailTemplateInput input = new PhishingEmailTemplateInput();
    input.setName("created-header-b");
    input.setSubject("subject-b");

    String response =
        mvc.perform(
                post(TEMPLATES)
                    .header("X-Tenant-Ids", tenantB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.phishing_email_template_id");
    assertEquals(tenantB, rawTenantId(createdId), "the created template must belong to tenant B");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    TenantContext.clearCurrentTenant();
    PhishingEmailTemplateInput input = new PhishingEmailTemplateInput();
    input.setName("no-selector");
    input.setSubject("subject");

    mvc.perform(
            post(TEMPLATES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant B's path: updating tenant A's template is not found")
  void updateCrossTenantIsRejected() throws Exception {
    PhishingEmailTemplateInput input = new PhishingEmailTemplateInput();
    input.setName("hijacked");
    input.setSubject("hijacked");

    mvc.perform(
            put(TENANT_TEMPLATES + "/{id}", tenantB, templateA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "under tenant B's path: deleting tenant A's template is not found and leaves it in place")
  void deleteCrossTenantIsRejected() throws Exception {
    mvc.perform(delete(TENANT_TEMPLATES + "/{id}", tenantB, templateA).with(csrf()))
        .andExpect(status().isNotFound());

    entityManager.flush();
    entityManager.clear();
    assertEquals(
        1L, rawCount(templateA), "tenant A's template must not have been deleted cross-tenant");
  }

  private String rawTenantId(String templateId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM phishing_email_templates WHERE"
                          + " phishing_email_template_id = ?")) {
                stmt.setString(1, templateId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String templateId) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT count(*) FROM phishing_email_templates WHERE"
                          + " phishing_email_template_id = ?")) {
                stmt.setString(1, templateId);
                try (var rows = stmt.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private String seedTemplate(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO phishing_email_templates (phishing_email_template_id,"
                + " phishing_email_template_name, phishing_email_template_subject,"
                + " phishing_email_template_add_tracking_pixel, phishing_email_template_created_at,"
                + " phishing_email_template_updated_at, tenant_id) VALUES (?1, ?2, ?3, true, now(),"
                + " now(), ?4)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, "subject-" + name)
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }
}
