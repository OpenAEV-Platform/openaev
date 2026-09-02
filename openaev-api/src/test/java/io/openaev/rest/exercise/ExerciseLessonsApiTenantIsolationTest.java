package io.openaev.rest.exercise;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.LessonsQuestionRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=lessons_templates")
@WithMockUser(isAdmin = true)
@DisplayName("ExerciseLessonsApi scopes the template lookup to the caller's tenants")
class ExerciseLessonsApiTenantIsolationTest extends IntegrationTest {

  private static final String APPLY_TEMPLATE =
      "/api/tenants/{tenantId}/exercises/{exerciseId}/lessons_apply_template/{lessonsTemplateId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @MockitoBean private ExerciseRepository exerciseRepository;
  @MockitoBean private LessonsCategoryRepository lessonsCategoryRepository;
  @MockitoBean private LessonsQuestionRepository lessonsQuestionRepository;

  private String tenantA;
  private String templateA;
  private String templateB;

  @BeforeEach
  void seedTwoTenantsWithOneTemplateEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("exercise-lessons-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("exercise-lessons-b").getId();
    templateA = seedTemplate(tenantA, "exercise-template-a");
    templateB = seedTemplate(tenantB, "exercise-template-b");

    when(exerciseRepository.findByIdAndTenantId(anyString(), anyString()))
        .thenReturn(Optional.of(new Exercise()));
    when(lessonsCategoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(lessonsCategoryRepository.findAll(any(Specification.class)))
        .thenReturn(List.<LessonsCategory>of());
    when(lessonsQuestionRepository.saveAll(any())).thenReturn(List.of());
  }

  @Test
  @DisplayName("under tenant A's path: A's own template is found")
  void underTenantAWithOwnTemplate() throws Exception {
    mvc.perform(post(APPLY_TEMPLATE, tenantA, UUID.randomUUID().toString(), templateA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("under tenant A's path: B's template is not found (cross-tenant blocked)")
  void underTenantAWithCrossTenantTemplateIsBlocked() throws Exception {
    mvc.perform(post(APPLY_TEMPLATE, tenantA, UUID.randomUUID().toString(), templateB).with(csrf()))
        .andExpect(status().isNotFound());
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
