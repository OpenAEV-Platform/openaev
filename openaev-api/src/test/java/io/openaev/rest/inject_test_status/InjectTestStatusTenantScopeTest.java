package io.openaev.rest.inject_test_status;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and they are removed on teardown.
 */
@WithMockUser(isAdmin = true)
@DisplayName("An inject test status read by id is resolved in the request scope")
class InjectTestStatusTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TEST_STATUS_PATH = "/injects/test/{testId}";

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
        "given a test status whose inject is in the scope when reading it then it is returned")
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

  private void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
  }

  // -- Seeds --

  /** A simulation, one of its injects and the inject's test status, all of the given tenant. */
  private SeededTestStatus seedTestStatus(String tenantId) {
    String simulationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " VALUES (?, ?, 'SCHEDULED', ?)",
        simulationId,
        "inject-test-status-scope-" + simulationId,
        tenantId);
    trackDelete("DELETE FROM exercises WHERE exercise_id = ?", simulationId);

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
            + " inject_depends_duration, inject_injector_contract, inject_exercise)"
            + " VALUES (?, ?, ?, false, true, 0, ?, ?)",
        injectId,
        tenantId,
        "inject-test-status-scope-" + injectId,
        contractId,
        simulationId);
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
