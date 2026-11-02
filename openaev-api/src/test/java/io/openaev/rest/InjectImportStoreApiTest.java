package io.openaev.rest;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.rest.scenario.response.ImportPostSummary;
import io.openaev.service.InjectImportService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The xls upload of the inject import is checked on the scenario or simulation the injects go into,
 * not on the mapper (#5347). A user who can edit that scenario or simulation can upload without the
 * tenant settings capability; a user who can only read it cannot. The service is mocked: these
 * tests only cover who may call the endpoint.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Xls upload for the inject import")
class InjectImportStoreApiTest extends IntegrationTest {

  public static final String SCENARIO_STORE = SCENARIO_URI + "/{scenarioId}/xls/store";
  public static final String SIMULATION_STORE = EXERCISE_URI + "/{exerciseId}/xls/store";

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  @MockitoBean private InjectImportService injectImportService;

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    exerciseComposer.reset();
    when(injectImportService.storeXlsFileForImport(any())).thenReturn(new ImportPostSummary());
  }

  private ResultActions upload(String uri, String resourceId) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "injects.xlsx", "application/octet-stream", new byte[] {1});
    return mvc.perform(multipart(uri, resourceId).file(file).with(csrf()));
  }

  @Nested
  @DisplayName("Into a scenario")
  class IntoScenario {

    private String persistScenario() {
      return scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get().getId();
    }

    @Test
    @DisplayName("A planner of the scenario can upload without the tenant settings capability")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_plannerGrant_should_storeFile() throws Exception {
      // Arrange
      String scenarioId = persistScenario();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.PLANNER, scenarioId);

      // Act & Assert
      upload(SCENARIO_STORE, scenarioId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A user who can manage every scenario can upload")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_ASSESSMENT},
        autoJoinDefaultTenant = true)
    void given_manageAssessmentCapability_should_storeFile() throws Exception {
      // Arrange
      String scenarioId = persistScenario();

      // Act & Assert
      upload(SCENARIO_STORE, scenarioId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("An observer of the scenario cannot upload")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_observerGrant_should_beForbidden() throws Exception {
      // Arrange
      String scenarioId = persistScenario();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.OBSERVER, scenarioId);

      // Act & Assert
      upload(SCENARIO_STORE, scenarioId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The mapper capability alone does not allow uploading into a scenario")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_TENANT_SETTINGS},
        autoJoinDefaultTenant = true)
    void given_tenantSettingsOnly_should_beForbidden() throws Exception {
      // Arrange
      String scenarioId = persistScenario();

      // Act & Assert
      upload(SCENARIO_STORE, scenarioId).andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Into a simulation")
  class IntoSimulation {

    private String persistSimulation() {
      return exerciseComposer.forExercise(ExerciseFixture.getExercise()).persist().get().getId();
    }

    @Test
    @DisplayName("A planner of the simulation can upload without the tenant settings capability")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_plannerGrant_should_storeFile() throws Exception {
      // Arrange
      String exerciseId = persistSimulation();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, exerciseId);

      // Act & Assert
      upload(SIMULATION_STORE, exerciseId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("An observer of the simulation cannot upload")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_observerGrant_should_beForbidden() throws Exception {
      // Arrange
      String exerciseId = persistSimulation();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, exerciseId);

      // Act & Assert
      upload(SIMULATION_STORE, exerciseId).andExpect(status().isForbidden());
    }
  }
}
