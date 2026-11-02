package io.openaev.rest;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Tenant;
import io.openaev.rest.scenario.form.InjectsImportInput;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The xls import of injects is checked on the scenario or simulation the injects go into, not on
 * the mapper (#5347). A user who can edit that scenario or simulation can upload the file and run
 * the import without the tenant settings capability; a user who cannot edit it is refused.
 *
 * <p>The upload runs outside any transaction ({@code NOT_SUPPORTED}), so its access and existence
 * checks only see committed rows: the arrange step is committed, then deleted after each test.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Xls import of injects")
class InjectImportStoreApiTest extends IntegrationTest {

  public static final String SCENARIO_XLS_URI = SCENARIO_URI + "/{scenarioId}/xls";
  public static final String SIMULATION_XLS_URI = EXERCISE_URI + "/{exerciseId}/xls";
  private static final String SHEET_NAME = "Timeline";

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  private final List<String> committedResourceIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    exerciseComposer.reset();
  }

  @AfterEach
  void deleteCommittedRows() {
    if (committedResourceIds.isEmpty()) {
      return;
    }
    // Roll back what the act step wrote (mapper, import), then delete the committed arrange rows.
    TestTransaction.end();
    TestTransaction.start();
    for (String id : committedResourceIds) {
      entityManager
          .createNativeQuery("DELETE FROM grants WHERE grant_resource = :id")
          .setParameter("id", id)
          .executeUpdate();
      entityManager
          .createNativeQuery("DELETE FROM scenarios WHERE scenario_id = :id")
          .setParameter("id", id)
          .executeUpdate();
      entityManager
          .createNativeQuery("DELETE FROM exercises WHERE exercise_id = :id")
          .setParameter("id", id)
          .executeUpdate();
    }
    committedResourceIds.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();
  }

  /** Commits the arrange step so the upload, which runs outside this transaction, can see it. */
  private void commitArrange(String resourceId) {
    committedResourceIds.add(resourceId);
    entityManager.flush();
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();
  }

  private ResultActions upload(String xlsUri, String resourceId) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "injects.xlsx", "application/octet-stream", workbook());
    return mvc.perform(multipart(xlsUri + "/store", resourceId).file(file).with(csrf()));
  }

  /** Uploads the file, then runs the dry run and the import with a mapper of the same tenant. */
  private void uploadAndImport(String xlsUri, String resourceId) throws Exception {
    String stored =
        upload(xlsUri, resourceId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String importId = JsonPath.read(stored, "$.import_id");

    InjectsImportInput input = new InjectsImportInput();
    input.setName(SHEET_NAME);
    input.setImportMapperId(seedMapper());
    input.setTimezoneOffset(0);
    for (String step : new String[] {"dry", "import"}) {
      mvc.perform(
              post(xlsUri + "/{importId}/" + step, resourceId, importId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());
    }
  }

  /** An xlsx file with one empty sheet: enough to go through the whole import. */
  private static byte[] workbook() throws Exception {
    try (Workbook wb = new XSSFWorkbook();
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      wb.createSheet(SHEET_NAME);
      wb.write(bos);
      return bos.toByteArray();
    }
  }

  /**
   * A mapper with no importers, in the default tenant the test user belongs to. Seeded after the
   * commit: the dry run and the import join the test transaction, so it is rolled back with it.
   */
  private String seedMapper() {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column, tenant_id)"
                + " VALUES (CAST(:id AS uuid), :name, :col, :tenant)")
        .setParameter("id", id)
        .setParameter("name", "xls-import-mapper")
        .setParameter("col", "A")
        .setParameter("tenant", Tenant.DEFAULT_TENANT_UUID)
        .executeUpdate();
    return id;
  }

  @Nested
  @DisplayName("Into a scenario")
  class IntoScenario {

    private String persistScenario() {
      return scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get().getId();
    }

    @Test
    @DisplayName("A planner of the scenario can upload and import without tenant settings")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_plannerGrant_should_uploadAndImport() throws Exception {
      // Arrange
      String scenarioId = persistScenario();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.PLANNER, scenarioId);
      commitArrange(scenarioId);

      // Act & Assert
      uploadAndImport(SCENARIO_XLS_URI, scenarioId);
    }

    @Test
    @DisplayName("A user who can manage every scenario can upload and import")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_ASSESSMENT},
        autoJoinDefaultTenant = true)
    void given_manageAssessmentCapability_should_uploadAndImport() throws Exception {
      // Arrange
      String scenarioId = persistScenario();
      commitArrange(scenarioId);

      // Act & Assert
      uploadAndImport(SCENARIO_XLS_URI, scenarioId);
    }

    @Test
    @DisplayName("Uploading into an unknown scenario is refused before the file is stored")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_ASSESSMENT},
        autoJoinDefaultTenant = true)
    void given_unknownScenario_should_returnNotFound() throws Exception {
      // Act & Assert
      upload(SCENARIO_XLS_URI, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An observer of the scenario cannot upload")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_observerGrant_should_beForbidden() throws Exception {
      // Arrange
      String scenarioId = persistScenario();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.OBSERVER, scenarioId);
      commitArrange(scenarioId);

      // Act & Assert
      upload(SCENARIO_XLS_URI, scenarioId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user with no access to the scenario cannot upload")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_noAccess_should_beForbidden() throws Exception {
      // Arrange
      String scenarioId = persistScenario();
      commitArrange(scenarioId);

      // Act & Assert
      upload(SCENARIO_XLS_URI, scenarioId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The mapper capability alone does not allow uploading into a scenario")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_TENANT_SETTINGS},
        autoJoinDefaultTenant = true)
    void given_tenantSettingsOnly_should_beForbidden() throws Exception {
      // Arrange
      String scenarioId = persistScenario();
      commitArrange(scenarioId);

      // Act & Assert
      upload(SCENARIO_XLS_URI, scenarioId).andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Into a simulation")
  class IntoSimulation {

    private String persistSimulation() {
      return exerciseComposer.forExercise(ExerciseFixture.getExercise()).persist().get().getId();
    }

    @Test
    @DisplayName("A planner of the simulation can upload and import without tenant settings")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_plannerGrant_should_uploadAndImport() throws Exception {
      // Arrange
      String exerciseId = persistSimulation();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, exerciseId);
      commitArrange(exerciseId);

      // Act & Assert
      uploadAndImport(SIMULATION_XLS_URI, exerciseId);
    }

    @Test
    @DisplayName("Uploading into an unknown simulation is refused before the file is stored")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_ASSESSMENT},
        autoJoinDefaultTenant = true)
    void given_unknownSimulation_should_returnNotFound() throws Exception {
      // Act & Assert
      upload(SIMULATION_XLS_URI, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An observer of the simulation cannot upload")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_observerGrant_should_beForbidden() throws Exception {
      // Arrange
      String exerciseId = persistSimulation();
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, exerciseId);
      commitArrange(exerciseId);

      // Act & Assert
      upload(SIMULATION_XLS_URI, exerciseId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user with no access to the simulation cannot upload")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_noAccess_should_beForbidden() throws Exception {
      // Arrange
      String exerciseId = persistSimulation();
      commitArrange(exerciseId);

      // Act & Assert
      upload(SIMULATION_XLS_URI, exerciseId).andExpect(status().isForbidden());
    }
  }
}
