package io.openaev.rest.finding;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("Finding search tenant isolation tests")
class FindingSearchApiTest extends IntegrationTest {


  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  @Autowired private FindingComposer findingComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  @BeforeEach
  void setUp() {
    findingComposer.reset();
    endpointComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Global search from tenant Y should not return findings created in tenant X")
    void given_findingInTenantX_should_notAppearInGlobalSearchFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      String response =
          performSearch("/api/tenants/" + tenantY.getId() + "/findings/search")
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals(Integer.valueOf(0), JsonPath.read(response, "$.totalElements"));
    }

    @Test
    @DisplayName("Scenario findings search from tenant Y should not access tenant X scenario")
    void given_scenarioInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/scenarios/"
                      + data.scenarioId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    @Test
    @DisplayName("Simulation findings search from tenant Y should not access tenant X simulation")
    void given_simulationInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/exercises/"
                      + data.simulationId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    @Test
    @DisplayName("Inject findings search from tenant Y should not access tenant X inject")
    void given_injectInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/injects/"
                      + data.injectId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    @Test
    @DisplayName("Endpoint findings search from tenant Y should not access tenant X endpoint")
    void given_endpointInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/endpoints/"
                      + data.endpointId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    private Tenant createTenantX() throws Exception {
      return tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X",
          Set.of(
              Capability.ACCESS_FINDINGS,
              Capability.ACCESS_ASSESSMENT,
              Capability.ACCESS_ASSETS,
              Capability.MANAGE_ASSESSMENT));
    }

    private Tenant createTenantY() throws Exception {
      return tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant Y",
          Set.of(
              Capability.ACCESS_FINDINGS, Capability.ACCESS_ASSESSMENT, Capability.ACCESS_ASSETS));
    }

    private TestData createFindingDataInTenant(String tenantId) {
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);

      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFindingWithRandomValue())
              .withEndpoint(endpointWrapper);

      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper);

      ExerciseComposer.Composer simulationWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createFinishedAttackExercise())
              .withInject(injectWrapper);

      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.getScenario())
              .withSimulation(simulationWrapper)
              .persist()
              .get();

      Exercise simulation = scenario.getExercises().getFirst();
      Inject inject = simulation.getInjects().getFirst();
      String endpointId = inject.getAssets().getFirst().getId();

      return new TestData(scenario.getId(), simulation.getId(), inject.getId(), endpointId);
    }

    private org.springframework.test.web.servlet.ResultActions performSearch(String uri)
        throws Exception {
      SearchPaginationInput input = PaginationFixture.getDefault().build();
      return mvc.perform(
          post(uri)
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()));
    }
  }

  /**
   * Covers the {@code finding_triage_status} distinct search filter added in {@link
   * FindingDistinctSearchService#extractTriageStatusSpecification}. The critical case is
   * UNTRIAGED: most findings never get a persisted {@code FindingTriage} row (see {@link
   * FindingTriageService} javadoc), so a naive equality filter would incorrectly exclude them.
   */
  @Nested
  @DisplayName("Triage status filter")
  @WithMockUser(
      withCapabilities = {Capability.ACCESS_FINDINGS, Capability.MANAGE_FINDING_TRIAGE})
  class TriageStatusFilter {

    private Finding createFinding() {
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject()).persist();
      return findingComposer
          .forFinding(FindingFixture.createDefaultTextFindingWithRandomValue())
          .withInject(injectWrapper)
          .persist()
          .get();
    }

    private void triage(String findingId, String status) throws Exception {
      JsonNode body =
          JsonNodeFactory.instance
              .objectNode()
              .put("status", status)
              .put("justification", "Triaged for finding_triage_status filter test");
      mvc.perform(
              patch("/api/findings/{id}/triage", findingId)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(body)))
          .andExpect(status().isOk());
    }

    private List<String> searchDistinctByTriageStatus(
        String status, Filters.FilterOperator operator) throws Exception {
      SearchPaginationInput input =
          PaginationFixture.simpleSearchWithAndOperator(
              "finding_triage_status", status, operator);
      String response =
          mvc.perform(
                  post("/api/findings/search")
                      .queryParam("distinct", "true")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.content[*].finding_id");
    }

    @Test
    @DisplayName("Filtering by CONFIRMED excludes never-triaged (no-row) findings")
    void given_confirmedFilter_should_onlyReturnExplicitlyConfirmedFindings() throws Exception {
      // Arrange
      Finding confirmed = createFinding();
      Finding neverTriaged = createFinding();
      triage(confirmed.getId(), "CONFIRMED");

      // Act
      List<String> ids = searchDistinctByTriageStatus("CONFIRMED", Filters.FilterOperator.eq);

      // Assert
      assertThat(ids).contains(confirmed.getId());
      assertThat(ids).doesNotContain(neverTriaged.getId());
    }

    @Test
    @DisplayName(
        "Filtering by UNTRIAGED returns both never-triaged (no-row) findings and explicit UNTRIAGED rows")
    void given_untriagedFilter_should_returnNeverTriagedFindings() throws Exception {
      // Arrange
      Finding neverTriaged = createFinding();
      Finding confirmed = createFinding();
      triage(confirmed.getId(), "CONFIRMED");

      // Act
      List<String> ids = searchDistinctByTriageStatus("UNTRIAGED", Filters.FilterOperator.eq);

      // Assert
      assertThat(ids).contains(neverTriaged.getId());
      assertThat(ids).doesNotContain(confirmed.getId());
    }

    @Test
    @DisplayName("not_eq CONFIRMED still includes never-triaged (no-row) findings")
    void given_notEqConfirmedFilter_should_includeNeverTriagedFindings() throws Exception {
      // Arrange - regression test for NULL-propagation: NOT(status = 'CONFIRMED') is SQL NULL
      // (neither true nor false) for a finding with no FindingTriage row, which would otherwise
      // incorrectly drop it from a "not_eq" result.
      Finding neverTriaged = createFinding();
      Finding confirmed = createFinding();
      triage(confirmed.getId(), "CONFIRMED");

      // Act
      List<String> ids = searchDistinctByTriageStatus("CONFIRMED", Filters.FilterOperator.not_eq);

      // Assert
      assertThat(ids).contains(neverTriaged.getId());
      assertThat(ids).doesNotContain(confirmed.getId());
    }
  }

  private record TestData(
      String scenarioId, String simulationId, String injectId, String endpointId) {}
}

