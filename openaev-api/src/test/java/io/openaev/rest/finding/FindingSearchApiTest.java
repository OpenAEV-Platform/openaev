package io.openaev.rest.finding;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
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
import jakarta.transaction.Transactional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
      Tenant tenantY = createTenantY();
      createFindingDataInTenant();

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
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant();

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
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant();

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
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant();

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
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant();

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

    private TestData createFindingDataInTenant() {
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

  private record TestData(
      String scenarioId, String simulationId, String injectId, String endpointId) {}
}
