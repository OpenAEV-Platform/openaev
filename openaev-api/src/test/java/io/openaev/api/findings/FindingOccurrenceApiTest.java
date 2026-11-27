package io.openaev.api.findings;

import static io.openaev.api.findings.FindingOccurrenceApi.TENANT_FINDING_OCCURRENCES_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.FindingFixture.createDefaultTextFindingWithRandomValue;
import static io.openaev.utils.fixtures.FindingOccurrenceFixture.createOccurrence;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Finding occurrences API")
class FindingOccurrenceApiTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @BeforeEach
  void setUp() {
    tenantIsolationHelper.attachCurrentUserToTenant(TenantContext.getCurrentTenant());
    findingComposer.reset();
    injectComposer.reset();
    simulationComposer.reset();
  }

  @Nested
  @DisplayName("When searching the occurrences of a finding")
  class WhenSearchingTheOccurrences {

    @Test
    @DisplayName("Should list each occurrence, most recent first, an occurrence without inject too")
    void given_twoOccurrences_should_listEachMostRecentFirst() throws Exception {
      // Arrange
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      Exercise simulation =
          simulationComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist()
              .get();
      Inject inject = injectWrapper.get();
      Finding finding =
          findingComposer
              .forFinding(createDefaultTextFindingWithRandomValue())
              .withInject(injectWrapper)
              .persist()
              .get();
      entityManager.persist(
          createOccurrence(finding, null, Instant.now().minus(1, ChronoUnit.DAYS), "older_field"));
      entityManager.flush();
      entityManager.clear();

      // Act
      ResultActions response = searchOccurrences(TenantContext.getCurrentTenant(), finding.getId());

      // Assert
      response
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(2))
          .andExpect(
              jsonPath("$.content[0].finding_occurrence_inject.inject_id").value(inject.getId()))
          .andExpect(
              jsonPath("$.content[0].finding_occurrence_simulation.context_id")
                  .value(simulation.getId()))
          .andExpect(jsonPath("$.content[1].finding_occurrence_inject").isEmpty())
          .andExpect(jsonPath("$.content[1].finding_occurrence_field").value("older_field"));
    }
  }

  @Nested
  @DisplayName("Tenant isolation")
  @WithMockUser
  class TenantIsolation {

    private Finding createFindingInTenant(String tenantId) {
      String previousTenant = TenantContext.getCurrentTenant();
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject()).persist();
      Finding finding =
          findingComposer
              .forFinding(createDefaultTextFindingWithRandomValue())
              .withInject(injectWrapper)
              .persist()
              .get();
      tenantIsolationHelper.switchToTenant(previousTenant, entityManager);
      return finding;
    }

    @Test
    @DisplayName("Should not list the occurrences of a finding of another tenant")
    void given_findingOfTenantX_should_listNoOccurrenceFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.ACCESS_FINDINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_FINDINGS));
      Finding finding = createFindingInTenant(tenantX.getId());
      entityManager.flush();
      entityManager.clear();

      // Act & Assert
      searchOccurrences(tenantY.getId(), finding.getId())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(0));
      searchOccurrences(tenantX.getId(), finding.getId())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(1));
    }
  }

  private ResultActions searchOccurrences(String tenantId, String findingId) throws Exception {
    SearchPaginationInput input = PaginationFixture.getDefault().build();
    input.setSorts(List.of(new SortField("finding_occurrence_date", "desc", null)));
    return mvc.perform(
        post(TENANT_FINDING_OCCURRENCES_URI + "/search", tenantId, findingId)
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }
}
