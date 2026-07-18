package io.openaev.rest.lessons_template;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
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

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=lessons_templates")
@WithMockUser(isAdmin = false)
@DisplayName("lessons_templates isolation holds for a non-admin spanning two tenants")
class LessonsTemplateNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_LESSONS = Set.of(Capability.ACCESS_LESSONS_LEARNED);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String templateA;
  private String templateB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneTemplateEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("lessons-nonadmin-a", READ_LESSONS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("lessons-nonadmin-b", READ_LESSONS).getId();
    templateA = seedTemplate(tenantA, "nonadmin-template-a");
    templateB = seedTemplate(tenantB, "nonadmin-template-b");
  }

  @Test
  @DisplayName("a non-admin searching under tenant A's path sees only A's template")
  void searchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
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
    assertTrue(
        response.contains(templateA), "A's template must appear for the non-admin member of A");
    assertFalse(response.contains(templateB), "B's template must not leak to A's scope");
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
