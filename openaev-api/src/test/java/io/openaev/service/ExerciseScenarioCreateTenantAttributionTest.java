package io.openaev.service;

import static io.openaev.database.model.TenantSettingKeys.TENANT_SIMULATION_DASHBOARD;
import static io.openaev.utils.constants.Constants.IMPORTED_OBJECT_NAME_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.dto.AutonomousRunCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.exercise.service.ExportService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.stix.StixService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CustomDashboardFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Every production path that creates a simulation or a scenario outside the two HTTP creates sets
 * the row's tenant itself: the parent's when the row is derived from one (a scenario launched as a
 * simulation, a duplicate, a coverage turned into a scenario), the request scope's otherwise (an
 * import with no parent, an autonomous run that provisions its own scenario). None of them may rely
 * on {@code TenantBaseListener}, which stamps the ambient {@link TenantContext} and goes away when
 * {@code exercises} and {@code scenarios} are activated.
 *
 * <p>Both tables are armed for this class only. The class is deliberately NOT
 * {@code @Transactional}: each path runs in its own committed transaction under tenant A's v2
 * scope, as its production callers do, and the row's tenant is read back through auto-committed
 * JDBC, which the statement inspector never rewrites. Where the path takes its parent as an
 * argument, the ambient {@link TenantContext} is pointed at a third tenant, so only the parent can
 * attribute the row.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=exercises,scenarios")
@WithMockUser(isAdmin = true)
@DisplayName("Simulation and scenario create paths attribute their own tenant")
class ExerciseScenarioCreateTenantAttributionTest extends IntegrationTest {

  private static final String STIX_BUNDLE =
      "src/test/resources/stix-bundles/security-coverage-without-objects.json";

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;
  @Autowired private DataSource dataSource;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ScenarioService scenarioService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private ScenarioToExerciseService scenarioToExerciseService;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ExportService exportService;
  @Autowired private ImportService importService;
  @Autowired private StixService stixService;
  @Autowired private AutonomousRunService autonomousRunService;

  // Only connectors configured at startup are known to the real bean, so a freshly seeded tenant
  // never has one; the coverage build needs an active connector to get past its first check.
  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantThird;

  @BeforeEach
  void seedTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = tenantHelper.createTenantWithCurrentUser("create-attr-a").getId();
    tenantThird = tenantHelper.createTenantWithCurrentUser("create-attr-third").getId();
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    // The mail reply-to collections reference their parent without a cascade, and the parents
    // reference each other, so they go first, in this order, before the tenant cascade.
    for (String tenant : new String[] {tenantA, tenantThird}) {
      jdbc.update(
          "DELETE FROM exercise_mails_reply_to WHERE exercise_id IN (SELECT exercise_id FROM"
              + " exercises WHERE tenant_id = ?)",
          tenant);
      jdbc.update(
          "DELETE FROM scenario_mails_reply_to WHERE scenario_id IN (SELECT scenario_id FROM"
              + " scenarios WHERE tenant_id = ?)",
          tenant);
      jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenant);
      jdbc.update("DELETE FROM scenarios WHERE tenant_id = ?", tenant);
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantThird);
  }

  @Nested
  @DisplayName("Scenario launched as a simulation")
  class ScenarioToSimulation {

    @Test
    @DisplayName("given_scenarioInTenantA_should_createSimulationInTenantA")
    void given_scenarioInTenantA_should_createSimulationInTenantA() {
      // Arrange
      String scenarioId = seedScenario(tenantA);

      // Act
      Exercise simulation =
          tenantScopedJobRunner.supplyInTenant(
              tenantA,
              () -> {
                Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
                TenantContext.setCurrentTenant(tenantThird);
                return scenarioToExerciseService.toExercise(scenario, null, false, null);
              });

      // Assert
      assertThat(exerciseTenant(simulation.getId()))
          .as("the simulation belongs to its scenario's tenant, not to the ambient one")
          .isEqualTo(tenantA);
    }

    @Test
    @DisplayName("given_dashboardSettingInTenantA_should_takeTheScenarioTenantDashboard")
    void given_dashboardSettingInTenantA_should_takeTheScenarioTenantDashboard() {
      // Arrange
      String scenarioId = seedScenario(tenantA);
      String dashboardA = seedSimulationDashboardSetting(tenantA);
      seedSimulationDashboardSetting(tenantThird);

      // Act
      Exercise simulation =
          tenantScopedJobRunner.supplyInTenant(
              tenantA,
              () -> {
                Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
                TenantContext.setCurrentTenant(tenantThird);
                return scenarioToExerciseService.toExercise(scenario, null, false, null);
              });

      // Assert
      assertThat(
              jdbc.queryForObject(
                  "SELECT exercise_custom_dashboard FROM exercises WHERE exercise_id = ?",
                  String.class,
                  simulation.getId()))
          .as("the default dashboard is read from the scenario's tenant settings")
          .isEqualTo(dashboardA);
    }
  }

  @Nested
  @DisplayName("Simulation duplicate")
  class SimulationDuplicate {

    @Test
    @DisplayName("given_simulationInTenantA_should_createDuplicateInTenantA")
    void given_simulationInTenantA_should_createDuplicateInTenantA() {
      // Arrange
      String exerciseId = seedExercise(tenantA);

      // Act
      Exercise duplicate =
          tenantScopedJobRunner.supplyInTenant(
              tenantA, () -> exerciseService.getDuplicateExercise(exerciseId));

      // Assert
      assertThat(duplicate.getId()).isNotEqualTo(exerciseId);
      assertThat(exerciseTenant(duplicate.getId()))
          .as("the duplicate belongs to its source's tenant")
          .isEqualTo(tenantA);
    }
  }

  @Nested
  @DisplayName("Import with no parent")
  class ImportWithoutParent {

    @Test
    @DisplayName("given_exerciseExportImportedUnderTenantA_should_createSimulationInTenantA")
    void given_exerciseExportImportedUnderTenantA_should_createSimulationInTenantA()
        throws Exception {
      // Arrange
      String exerciseId = seedExercise(tenantA);
      String exportedName =
          tenantScopedJobRunner.supplyInTenant(
              tenantA, () -> exerciseRepository.findById(exerciseId).orElseThrow().getName());
      byte[] zip =
          tenantScopedJobRunner.supplyInTenant(
              tenantA,
              () -> {
                try {
                  return exportService.exportExerciseToZip(
                      exerciseRepository.findById(exerciseId).orElseThrow(),
                      ExportOptions.mask(false, false, false),
                      false);
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });

      // Act
      importUnderTenantA(zip);

      // Assert
      assertThat(
              jdbc.queryForList(
                  "SELECT tenant_id FROM exercises WHERE exercise_name = ?",
                  String.class,
                  exportedName + IMPORTED_OBJECT_NAME_SUFFIX))
          .as("the imported simulation belongs to the import's write tenant")
          .containsExactly(tenantA);
    }

    @Test
    @DisplayName("given_scenarioExportImportedUnderTenantA_should_createScenarioInTenantA")
    void given_scenarioExportImportedUnderTenantA_should_createScenarioInTenantA()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(tenantA);
      String exportedName =
          tenantScopedJobRunner.supplyInTenant(
              tenantA, () -> scenarioRepository.findById(scenarioId).orElseThrow().getName());
      byte[] zip =
          tenantScopedJobRunner.supplyInTenant(
              tenantA,
              () -> {
                MockHttpServletResponse response = new MockHttpServletResponse();
                try {
                  scenarioService.exportScenario(scenarioId, false, false, false, false, response);
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
                return response.getContentAsByteArray();
              });

      // Act
      importUnderTenantA(zip);

      // Assert
      assertThat(
              jdbc.queryForList(
                  "SELECT tenant_id FROM scenarios WHERE scenario_name = ?",
                  String.class,
                  exportedName + IMPORTED_OBJECT_NAME_SUFFIX))
          .as("the imported scenario belongs to the import's write tenant")
          .containsExactly(tenantA);
    }

    private void importUnderTenantA(byte[] zip) {
      tenantScopedJobRunner.supplyInTenant(
          tenantA,
          () -> {
            try {
              return importService.handleInputStreamFileImport(
                  TxCtx.forTenant(tenantA),
                  new ByteArrayInputStream(zip),
                  null,
                  null,
                  null,
                  null,
                  IMPORTED_OBJECT_NAME_SUFFIX);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          });
    }
  }

  @Nested
  @DisplayName("Security coverage bundle")
  class SecurityCoverageBundle {

    @Test
    @DisplayName("given_coverageBundleUnderTenantA_should_createScenarioInTenantA")
    void given_coverageBundleUnderTenantA_should_createScenarioInTenantA() throws Exception {
      // Arrange
      ConnectorBase connectorBase = mock(ConnectorBase.class);
      when(connectorBase.getUrl()).thenReturn("http://localhost/opencti");
      when(openCTIConnectorService.getConnectorBase(eq(tenantA)))
          .thenReturn(Optional.of(connectorBase));
      JsonNode event = objectMapper.readTree(new File(STIX_BUNDLE)).get("event");
      String bundle = objectMapper.writeValueAsString(event.get("stix_objects"));

      // Act
      Scenario scenario =
          tenantScopedJobRunner.supplyInTenant(
              tenantA,
              () -> {
                try {
                  return stixService.processBundle(TxCtx.forTenant(tenantA), bundle);
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });

      // Assert
      assertThat(scenarioTenant(scenario.getId()))
          .as("the scenario belongs to its coverage's tenant")
          .isEqualTo(tenantA);
    }
  }

  @Nested
  @DisplayName("Autonomous run with no scenario")
  class AutonomousRunProvisioning {

    @Test
    @DisplayName("given_runCreatedUnderTenantA_should_provisionScenarioAndSimulationInTenantA")
    void given_runCreatedUnderTenantA_should_provisionScenarioAndSimulationInTenantA() {
      // Arrange
      AutonomousRunCreateInput input = new AutonomousRunCreateInput();
      input.setObjective("create-attr-objective-" + UUID.randomUUID());
      input.setPlanMode(true);

      // Act
      AutonomousRun run =
          tenantScopedJobRunner.supplyInTenant(
              tenantA,
              () -> {
                TenantContext.setCurrentTenant(tenantThird);
                return autonomousRunService.create(TxCtx.forTenant(tenantA), input);
              });

      // Assert
      assertThat(scenarioTenant(run.getScenarioId()))
          .as("the provisioned scenario belongs to the request scope's tenant")
          .isEqualTo(tenantA);
      assertThat(exerciseTenant(run.getSimulationId()))
          .as("the run's simulation belongs to its scenario's tenant")
          .isEqualTo(tenantA);
    }
  }

  private String seedScenario(String tenantId) {
    return tenantScopedJobRunner.supplyInTenant(
        tenantId,
        () -> {
          Scenario scenario = ScenarioFixture.getScenario();
          scenario.setName("create-attr-scenario-" + UUID.randomUUID());
          scenario.setTenant(new Tenant(tenantId));
          return scenarioService.createScenario(scenario).getId();
        });
  }

  private String seedExercise(String tenantId) {
    return tenantScopedJobRunner.supplyInTenant(
        tenantId,
        () -> {
          Exercise exercise = ExerciseFixture.createDefaultExercise();
          exercise.setName("create-attr-exercise-" + UUID.randomUUID());
          exercise.setTenant(new Tenant(tenantId));
          return exerciseRepository.save(exercise).getId();
        });
  }

  private String seedSimulationDashboardSetting(String tenantId) {
    return tenantScopedJobRunner.supplyInTenant(
        tenantId,
        () -> {
          CustomDashboard dashboard = CustomDashboardFixture.createDefaultCustomDashboard();
          dashboard.setTenant(new Tenant(tenantId));
          String dashboardId = customDashboardRepository.save(dashboard).getId();
          Setting setting =
              settingRepository
                  .findByKeyAndTenantId(TENANT_SIMULATION_DASHBOARD.key(), tenantId)
                  .orElseGet(
                      () -> {
                        Setting created = new Setting();
                        created.setKey(TENANT_SIMULATION_DASHBOARD.key());
                        created.setTenant(new Tenant(tenantId));
                        return created;
                      });
          setting.setValue(dashboardId);
          settingRepository.save(setting);
          return dashboardId;
        });
  }

  private String exerciseTenant(String exerciseId) {
    List<String> tenants =
        jdbc.queryForList(
            "SELECT tenant_id FROM exercises WHERE exercise_id = ?", String.class, exerciseId);
    assertThat(tenants).as("exactly one simulation row").hasSize(1);
    return tenants.getFirst();
  }

  private String scenarioTenant(String scenarioId) {
    List<String> tenants =
        jdbc.queryForList(
            "SELECT tenant_id FROM scenarios WHERE scenario_id = ?", String.class, scenarioId);
    assertThat(tenants).as("exactly one scenario row").hasSize(1);
    return tenants.getFirst();
  }
}
