package io.openaev.rest.lessons_template;

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
import io.openaev.rest.lessons_template.form.LessonsTemplateCategoryInput;
import io.openaev.rest.lessons_template.form.LessonsTemplateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
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

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=lessons_templates")
@WithMockUser(isAdmin = true)
@DisplayName("lessons_templates read and write isolation through the real HTTP endpoint")
class LessonsTemplateHttpIsolationTest extends IntegrationTest {

  private static final String TEMPLATE_BY_ID =
      "/api/tenants/{tenantId}/lessons_templates/{lessonsTemplateId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String templateA;
  private String templateB;

  @BeforeEach
  void seedTwoTenantsWithOneTemplateEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("lessons-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("lessons-http-iso-b").getId();
    templateA = seedTemplate(tenantA, "template-a");
    templateB = seedTemplate(tenantB, "template-b");
  }

  @Test
  @DisplayName("under tenant A's path: list returns A's template and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/lessons_templates", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(templateA), "A's template must appear in A's list");
    assertFalse(response.contains(templateB), "B's template must not appear in A's list");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's template and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/lessons_templates/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(templateA), "A's template must appear in A's search results");
    assertFalse(response.contains(templateB), "B's template must not appear in A's search results");
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header (no path tenant): list returns A's template and not B's")
  void listViaHeaderReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get("/api/lessons_templates").header("X-Tenant-Ids", tenantA).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(templateA), "A's template must appear when A is selected");
    assertFalse(response.contains(templateB), "B's template must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: creating a template attributes it to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    LessonsTemplateInput input = new LessonsTemplateInput();
    input.setName("created-under-a");
    input.setDescription("created");
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/lessons_templates", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.lessonstemplate_id");
    String storedTenant =
        (String)
            entityManager
                .createNativeQuery(
                    "SELECT tenant_id FROM lessons_templates WHERE lessons_template_id = :id")
                .setParameter("id", createdId)
                .getSingleResult();
    assertEquals(tenantA, storedTenant, "the created template must belong to tenant A");
  }

  @Test
  @DisplayName("creating with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    LessonsTemplateInput input = new LessonsTemplateInput();
    input.setName("no-selector");
    input.setDescription("none");
    mvc.perform(
            post("/api/lessons_templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: updating A's own template works")
  void updateUnderTenantAUpdatesOwnTemplate() throws Exception {
    mvc.perform(
            put(TEMPLATE_BY_ID, tenantA, templateA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(templateInput("renamed-a", "updated")))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(templateA), "A's own template must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's template is not found and leaves it untouched")
  void updateUnderTenantAOfBTemplateIsBlocked() throws Exception {
    mvc.perform(
            put(TEMPLATE_BY_ID, tenantA, templateB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(templateInput("hijacked", "hijacked")))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("template-b", rawName(templateB), "B's template must be untouched by tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's template is a no-op and leaves it in place")
  void deleteUnderTenantAOfBTemplateIsBlocked() throws Exception {
    mvc.perform(delete(TEMPLATE_BY_ID, tenantA, templateB).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(1L, rawCount(templateB), "B's template must survive tenant A's delete attempt");
  }

  @Test
  @DisplayName(
      "under tenant A's path: creating a category under B's template is blocked by scoped lookup")
  void createCategoryUnderTenantAForBTemplateIsBlocked() throws Exception {
    LessonsTemplateCategoryInput input = new LessonsTemplateCategoryInput();
    input.setName("category");
    input.setDescription("description");
    input.setOrder(1);
    mvc.perform(
            post(
                    "/api/tenants/{tenantId}/lessons_templates/{lessonsTemplateId}/lessons_template_categories",
                    tenantA,
                    templateB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  private static LessonsTemplateInput templateInput(String name, String description) {
    LessonsTemplateInput input = new LessonsTemplateInput();
    input.setName(name);
    input.setDescription(description);
    return input;
  }

  private String rawName(String templateId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT lessons_template_name FROM lessons_templates WHERE lessons_template_id = ?")) {
                statement.setString(1, templateId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String templateId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM lessons_templates WHERE lessons_template_id = ?")) {
                statement.setString(1, templateId);
                try (ResultSet rows = statement.executeQuery()) {
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
            "INSERT INTO lessons_templates (lessons_template_id, lessons_template_name, lessons_template_description, tenant_id)"
                + " VALUES (:id, :name, :description, :tenant)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("description", name)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }
}
