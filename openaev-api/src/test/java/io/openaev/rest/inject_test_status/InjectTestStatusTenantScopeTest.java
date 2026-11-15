package io.openaev.rest.inject_test_status;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * An inject test status read by id is answered only when its inject lies in the request scope. A
 * test status whose inject is outside it is answered like an unknown id: 404.
 *
 * <p>An inject test status is deleted only when its inject belongs to the simulation or scenario of
 * the path. A test status of any other inject, of another tenant or of another simulation or
 * scenario of the same tenant, is answered like an unknown id (404) and its row is left in place.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and they are removed on teardown.
 */
@WithMockUser(isAdmin = true)
@DisplayName("An inject test status read or deleted by id is resolved in the request scope")
class InjectTestStatusTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TEST_STATUS_PATH = "/injects/test/{testId}";
  private static final String PATH_TEST_STATUS_PATH = "/{pathId}/injects/test/{testId}";
  private static final String PATH_INJECT_TEST_PATH = "/{pathId}/injects/{injectId}/test";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private final List<Object[]> deletes = new ArrayList<>();

  private record SeededTestStatus(String injectId, String testId) {}

  private record SeededParent(String tenantId, String parentId, String injectParentColumn) {}

  @BeforeEach
  void seedTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // The caller is a member of the default tenant first, then of a tenant of its own.
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    tenantB = tenantHelper.createTenantWithCurrentUser("inject-test-status-scope").getId();
    assertNotEquals(DEFAULT_TENANT, tenantB);
  }

  @AfterEach
  void cleanup() {
    // Reverse insertion order: children before the rows they reference.
    for (int i = deletes.size() - 1; i >= 0; i--) {
      Object[] delete = deletes.get(i);
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    deletes.clear();
    tenantHelper.deleteCommittedTenants(tenantB);
  }

  @Nested
  @DisplayName("Through the header route")
  class ThroughHeaderRoute {

    @Test
    @DisplayName(
        "given a test status whose inject is of a tenant outside the scope when reading it then it is not found")
    void given_testStatusOfTenantOutsideScope_should_answerNotFound() throws Exception {
      // Arrange
      SeededTestStatus foreign = seedTestStatus(tenantB);
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              get(EXERCISE_URI + TEST_STATUS_PATH, foreign.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      result.andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a test status whose inject is of a tenant outside the scope when reading it through the scenario path then it is not found")
    void given_testStatusOfTenantOutsideScope_should_answerNotFoundThroughScenarioPath()
        throws Exception {
      // Arrange
      SeededTestStatus foreign = seedTestStatus(tenantB);
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              get(SCENARIO_URI + TEST_STATUS_PATH, foreign.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      result.andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a test status whose inject is in the scope when reading it then the response holds it")
    void given_testStatusInScope_should_returnIt() throws Exception {
      // Arrange
      SeededTestStatus own = seedTestStatus(DEFAULT_TENANT);
      clearAmbientTenant();

      // Act
      String response =
          mvc.perform(
                  get(EXERCISE_URI + TEST_STATUS_PATH, own.testId())
                      .header(TENANT_HEADER, DEFAULT_TENANT)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("inject_id").isEqualTo(own.injectId());
    }

    @Test
    @DisplayName("given an unknown test status id when reading it then it is not found")
    void given_unknownTestStatus_should_answerNotFound() throws Exception {
      // Arrange
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              get(EXERCISE_URI + TEST_STATUS_PATH, UUID.randomUUID().toString())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      result.andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Through the tenant route")
  class ThroughTenantRoute {

    @Test
    @DisplayName(
        "given a test status whose inject is of another tenant when reading it through the tenant path then it is not found")
    void given_testStatusOfOtherTenant_should_answerNotFoundThroughTenantPath() throws Exception {
      // Arrange
      SeededTestStatus foreign = seedTestStatus(DEFAULT_TENANT);

      // Act
      ResultActions result =
          mvc.perform(
              get(TENANT_EXERCISE_URI + TEST_STATUS_PATH, tenantB, foreign.testId())
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      result.andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Deleting through the simulation path")
  class DeletingThroughSimulationPath {

    @Test
    @DisplayName(
        "given a test status of a simulation of another tenant in the scope when deleting it through the path of a simulation then it is not found and left in place")
    void given_testStatusOfSimulationOfOtherTenant_should_answerNotFoundAndLeaveIt()
        throws Exception {
      // Arrange
      SeededParent pathSimulation = seedSimulation(DEFAULT_TENANT);
      SeededTestStatus foreign = seedTestStatus(seedSimulation(tenantB));
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              delete(
                      EXERCISE_URI + PATH_TEST_STATUS_PATH,
                      pathSimulation.parentId(),
                      foreign.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT + "," + tenantB)
                  .with(csrf()));

      // Assert
      result.andExpect(status().isNotFound());
      assertTestStatusPresent(foreign);
    }

    @Test
    @DisplayName(
        "given a test status of another simulation of the same tenant when deleting it through the path of a simulation then it is not found and left in place")
    void given_testStatusOfOtherSimulationOfSameTenant_should_answerNotFoundAndLeaveIt()
        throws Exception {
      // Arrange
      SeededParent pathSimulation = seedSimulation(DEFAULT_TENANT);
      SeededTestStatus other = seedTestStatus(seedSimulation(DEFAULT_TENANT));
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              delete(
                      EXERCISE_URI + PATH_TEST_STATUS_PATH,
                      pathSimulation.parentId(),
                      other.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .with(csrf()));

      // Assert
      result.andExpect(status().isNotFound());
      assertTestStatusPresent(other);
    }

    @Test
    @DisplayName(
        "given a test status of the simulation of the path when deleting it then it is deleted")
    void given_testStatusOfPathSimulation_should_deleteIt() throws Exception {
      // Arrange
      SeededParent pathSimulation = seedSimulation(DEFAULT_TENANT);
      SeededTestStatus own = seedTestStatus(pathSimulation);
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              delete(EXERCISE_URI + PATH_TEST_STATUS_PATH, pathSimulation.parentId(), own.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .with(csrf()));

      // Assert
      result.andExpect(status().isOk());
      assertTestStatusAbsent(own);
    }

    @Test
    @DisplayName(
        "given a test status of a simulation of another tenant when deleting it through the tenant path of a simulation then it is not found and left in place")
    void given_testStatusOfSimulationOfOtherTenant_should_answerNotFoundThroughTenantPath()
        throws Exception {
      // Arrange
      SeededParent pathSimulation = seedSimulation(tenantB);
      SeededTestStatus foreign = seedTestStatus(seedSimulation(DEFAULT_TENANT));

      // Act
      ResultActions result =
          mvc.perform(
              delete(
                      TENANT_EXERCISE_URI + PATH_TEST_STATUS_PATH,
                      tenantB,
                      pathSimulation.parentId(),
                      foreign.testId())
                  .with(csrf()));

      // Assert
      result.andExpect(status().isNotFound());
      assertTestStatusPresent(foreign);
    }
  }

  @Nested
  @DisplayName("Deleting through the scenario path")
  class DeletingThroughScenarioPath {

    @Test
    @DisplayName(
        "given a test status of a scenario of another tenant in the scope when deleting it through the path of a scenario then it is not found and left in place")
    void given_testStatusOfScenarioOfOtherTenant_should_answerNotFoundAndLeaveIt()
        throws Exception {
      // Arrange
      SeededParent pathScenario = seedScenario(DEFAULT_TENANT);
      SeededTestStatus foreign = seedTestStatus(seedScenario(tenantB));
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              delete(
                      SCENARIO_URI + PATH_TEST_STATUS_PATH,
                      pathScenario.parentId(),
                      foreign.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT + "," + tenantB)
                  .with(csrf()));

      // Assert
      result.andExpect(status().isNotFound());
      assertTestStatusPresent(foreign);
    }

    @Test
    @DisplayName(
        "given a test status of another scenario of the same tenant when deleting it through the path of a scenario then it is not found and left in place")
    void given_testStatusOfOtherScenarioOfSameTenant_should_answerNotFoundAndLeaveIt()
        throws Exception {
      // Arrange
      SeededParent pathScenario = seedScenario(DEFAULT_TENANT);
      SeededTestStatus other = seedTestStatus(seedScenario(DEFAULT_TENANT));
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              delete(SCENARIO_URI + PATH_TEST_STATUS_PATH, pathScenario.parentId(), other.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .with(csrf()));

      // Assert
      result.andExpect(status().isNotFound());
      assertTestStatusPresent(other);
    }

    @Test
    @DisplayName(
        "given a test status of a simulation when deleting it through the path of a scenario then it is not found and left in place")
    void given_testStatusOfSimulation_should_answerNotFoundThroughScenarioPath() throws Exception {
      // Arrange
      SeededParent pathScenario = seedScenario(DEFAULT_TENANT);
      SeededTestStatus simulationTest = seedTestStatus(seedSimulation(DEFAULT_TENANT));
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              delete(
                      SCENARIO_URI + PATH_TEST_STATUS_PATH,
                      pathScenario.parentId(),
                      simulationTest.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .with(csrf()));

      // Assert
      result.andExpect(status().isNotFound());
      assertTestStatusPresent(simulationTest);
    }

    @Test
    @DisplayName(
        "given a test status of the scenario of the path when deleting it then it is deleted")
    void given_testStatusOfPathScenario_should_deleteIt() throws Exception {
      // Arrange
      SeededParent pathScenario = seedScenario(DEFAULT_TENANT);
      SeededTestStatus own = seedTestStatus(pathScenario);
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              delete(SCENARIO_URI + PATH_TEST_STATUS_PATH, pathScenario.parentId(), own.testId())
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .with(csrf()));

      // Assert
      result.andExpect(status().isOk());
      assertTestStatusAbsent(own);
    }

    @Test
    @DisplayName(
        "given a test status of a scenario of another tenant when deleting it through the tenant path of a scenario then it is not found and left in place")
    void given_testStatusOfScenarioOfOtherTenant_should_answerNotFoundThroughTenantPath()
        throws Exception {
      // Arrange
      SeededParent pathScenario = seedScenario(tenantB);
      SeededTestStatus foreign = seedTestStatus(seedScenario(DEFAULT_TENANT));

      // Act
      ResultActions result =
          mvc.perform(
              delete(
                      TENANT_SCENARIO_URI + PATH_TEST_STATUS_PATH,
                      tenantB,
                      pathScenario.parentId(),
                      foreign.testId())
                  .with(csrf()));

      // Assert
      result.andExpect(status().isNotFound());
      assertTestStatusPresent(foreign);
    }
  }

  @Nested
  @DisplayName("Testing an inject through the path of a simulation or scenario")
  class TestingThroughPath {

    @Test
    @DisplayName(
        "given an inject of a simulation of another tenant in the scope when testing it through the path of a simulation then it is not found")
    void given_injectOfSimulationOfOtherTenant_should_answerNotFound() throws Exception {
      // Arrange
      SeededParent pathSimulation = seedSimulation(DEFAULT_TENANT);
      SeededTestStatus foreign = seedTestStatus(seedSimulation(tenantB));
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      EXERCISE_URI + PATH_INJECT_TEST_PATH,
                      pathSimulation.parentId(),
                      foreign.injectId())
                  .header(TENANT_HEADER, DEFAULT_TENANT + "," + tenantB)
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      result.andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given an inject of a scenario of another tenant in the scope when testing it through the path of a scenario then it is not found")
    void given_injectOfScenarioOfOtherTenant_should_answerNotFound() throws Exception {
      // Arrange
      SeededParent pathScenario = seedScenario(DEFAULT_TENANT);
      SeededTestStatus foreign = seedTestStatus(seedScenario(tenantB));
      clearAmbientTenant();

      // Act
      ResultActions result =
          mvc.perform(
              get(SCENARIO_URI + PATH_INJECT_TEST_PATH, pathScenario.parentId(), foreign.injectId())
                  .header(TENANT_HEADER, DEFAULT_TENANT + "," + tenantB)
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      result.andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given an inject of a simulation of another tenant when testing it through the tenant path of a simulation then it is not found")
    void given_injectOfSimulationOfOtherTenant_should_answerNotFoundThroughTenantPath()
        throws Exception {
      // Arrange
      SeededParent pathSimulation = seedSimulation(tenantB);
      SeededTestStatus foreign = seedTestStatus(seedSimulation(DEFAULT_TENANT));

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      TENANT_EXERCISE_URI + PATH_INJECT_TEST_PATH,
                      tenantB,
                      pathSimulation.parentId(),
                      foreign.injectId())
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      result.andExpect(status().isNotFound());
    }
  }

  private void assertTestStatusPresent(SeededTestStatus testStatus) {
    assertEquals(1, countTestStatus(testStatus));
  }

  private void assertTestStatusAbsent(SeededTestStatus testStatus) {
    assertEquals(0, countTestStatus(testStatus));
  }

  private int countTestStatus(SeededTestStatus testStatus) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM injects_tests_statuses WHERE status_id = ?",
        Integer.class,
        testStatus.testId());
  }

  private void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
  }

  // -- Seeds --

  /** A simulation, one of its injects and the inject's test status, all of the given tenant. */
  private SeededTestStatus seedTestStatus(String tenantId) {
    return seedTestStatus(seedSimulation(tenantId));
  }

  private SeededParent seedSimulation(String tenantId) {
    String simulationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " VALUES (?, ?, 'SCHEDULED', ?)",
        simulationId,
        "inject-test-status-scope-" + simulationId,
        tenantId);
    trackDelete("DELETE FROM exercises WHERE exercise_id = ?", simulationId);
    return new SeededParent(tenantId, simulationId, "inject_exercise");
  }

  private SeededParent seedScenario(String tenantId) {
    String scenarioId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id) VALUES (?, ?, ?)",
        scenarioId,
        "inject-test-status-scope-" + scenarioId,
        tenantId);
    trackDelete("DELETE FROM scenarios WHERE scenario_id = ?", scenarioId);
    return new SeededParent(tenantId, scenarioId, "inject_scenario");
  }

  /**
   * One inject of the given simulation or scenario, in its tenant, and the inject's test status.
   */
  private SeededTestStatus seedTestStatus(SeededParent parent) {
    String tenantId = parent.tenantId();
    String contractId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content, injector_contract_import_available)"
            + " VALUES (?, ?, '{\"fields\": []}', false)",
        contractId,
        tenantId);
    trackDelete("DELETE FROM injectors_contracts WHERE injector_contract_id = ?", contractId);

    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_injector_contract, "
            + parent.injectParentColumn()
            + ") VALUES (?, ?, ?, false, true, 0, ?, ?)",
        injectId,
        tenantId,
        "inject-test-status-scope-" + injectId,
        contractId,
        parent.parentId());
    trackDelete("DELETE FROM injects WHERE inject_id = ?", injectId);

    String testId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects_tests_statuses (status_id, status_name, status_inject)"
            + " VALUES (?, 'EXECUTED', ?)",
        testId,
        injectId);
    trackDelete("DELETE FROM injects_tests_statuses WHERE status_id = ?", testId);
    assertEquals(
        tenantId,
        jdbc.queryForObject(
            "SELECT tenant_id FROM injects WHERE inject_id = ?", String.class, injectId));
    return new SeededTestStatus(injectId, testId);
  }

  private void trackDelete(String sql, Object... params) {
    Object[] statement = new Object[params.length + 1];
    System.arraycopy(params, 0, statement, 1, params.length);
    statement[0] = sql;
    deletes.add(statement);
  }
}
