package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.utils.TagLinkJdbc;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A simulation's logos resolve the document id named in the body within the simulation's own
 * tenant: an id of another tenant is treated like an unknown one, the same as the pre-existing
 * behaviour on a wholly unknown id (the logo is set to {@code null}, not refused).
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A simulation's logos resolve their documents within the simulation's own tenant")
class ExerciseLogoDocumentLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  private static final String EXERCISE_LOGO_DARK =
      "SELECT exercise_logo_dark FROM exercises WHERE exercise_id = ?";
  private static final String EXERCISE_LOGO_LIGHT =
      "SELECT exercise_logo_light FROM exercises WHERE exercise_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseService exerciseService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private EntityManager entityManager;

  private String otherTenant;
  private String exercise;
  private String documentInOther;
  private String documentInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("exercise-logo-link-o").getId();

    Exercise createdExercise = new Exercise();
    createdExercise.setName("exercise-logo-link-exercise");
    createdExercise.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    exercise = exerciseService.createExercise(createdExercise).getId();

    documentInOther = saveDocument(otherTenant);
    documentInDefault = saveDocument(Tenant.DEFAULT_TENANT_UUID);

    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "given a simulation of the default tenant when its logos are set to a document of another"
          + " tenant, named through the header route, then the request is refused and the logos stay unset")
  void given_bodyNamingTheOtherTenant_should_refuseThroughHeader() throws Exception {
    // -- Act & Assert --
    mvc.perform(
            put(EXERCISE_URI + "/{exerciseId}/logos", exercise)
                .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(logosBody(documentInOther))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertThat(logoColumn(EXERCISE_LOGO_DARK)).containsExactly((String) null);
    assertThat(logoColumn(EXERCISE_LOGO_LIGHT)).containsExactly((String) null);
  }

  @Test
  @DisplayName(
      "given a simulation of the default tenant when its logos are set to its own document,"
          + " named through the header route, then the logos are set")
  void given_bodyNamingItsOwnDocument_should_setLogosThroughHeader() throws Exception {
    // -- Act & Assert --
    mvc.perform(
            put(EXERCISE_URI + "/{exerciseId}/logos", exercise)
                .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(logosBody(documentInDefault))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exercise_logo_dark").value(documentInDefault))
        .andExpect(jsonPath("$.exercise_logo_light").value(documentInDefault));
  }

  @Test
  @DisplayName(
      "given a simulation of the default tenant when its logos are set to a document of another"
          + " tenant, named under its own tenant path, then the request is refused and the logos stay unset")
  void given_bodyNamingTheOtherTenant_should_refuseOnTenantRoute() throws Exception {
    // -- Act & Assert --
    mvc.perform(
            put(TENANT_EXERCISE_URI + "/{exerciseId}/logos", Tenant.DEFAULT_TENANT_UUID, exercise)
                .contentType(MediaType.APPLICATION_JSON)
                .content(logosBody(documentInOther))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertThat(logoColumn(EXERCISE_LOGO_DARK)).containsExactly((String) null);
    assertThat(logoColumn(EXERCISE_LOGO_LIGHT)).containsExactly((String) null);
  }

  // -- Helpers --

  private List<String> logoColumn(String sql) {
    return TagLinkJdbc.column(entityManager, sql, exercise);
  }

  private static String logosBody(String documentId) {
    return "{\"exercise_logo_dark\": \""
        + documentId
        + "\", \"exercise_logo_light\": \""
        + documentId
        + "\"}";
  }

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("exercise-logo-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
  }
}
