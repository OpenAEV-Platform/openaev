package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Not {@code @Transactional}: the launch must commit before its response is written, as in
 * production, so that the response reads the simulation outside the request's tenant scope.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Launching a scenario whose inject has a document attached")
class ScenarioLaunchWithInjectDocumentTest extends IntegrationTest {

  private static final String TENANT = Tenant.DEFAULT_TENANT_UUID;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final String scenarioId = UUID.randomUUID().toString();
  private final String contractId = UUID.randomUUID().toString();
  private final String injectId = UUID.randomUUID().toString();
  private final String documentId = UUID.randomUUID().toString();

  @BeforeEach
  void seedScenario() {
    jdbc = new JdbcTemplate(dataSource);
    tenantHelper.attachCurrentUserToTenant(TENANT);
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id) VALUES (?, ?, ?)",
        scenarioId,
        "launch-with-document-" + scenarioId,
        TENANT);
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content, injector_contract_import_available)"
            + " VALUES (?, ?, '{}', false)",
        contractId,
        TENANT);
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_injector_contract, inject_scenario)"
            + " VALUES (?, ?, ?, false, true, 0, ?, ?)",
        injectId,
        TENANT,
        "launch-with-document-" + injectId,
        contractId,
        scenarioId);
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'text/plain', ?, ?)",
        documentId,
        "launch-with-document.txt",
        "launch-with-document/" + documentId,
        TENANT);
    jdbc.update(
        "INSERT INTO injects_documents (inject_id, document_id, document_attached)"
            + " VALUES (?, ?, true)",
        injectId,
        documentId);
  }

  @AfterEach
  void cleanup() {
    List<String> simulationIds =
        jdbc.queryForList(
            "SELECT exercise_id FROM scenarios_exercises WHERE scenario_id = ?",
            String.class,
            scenarioId);
    jdbc.update("DELETE FROM injects_documents WHERE document_id = ?", documentId);
    for (String simulationId : simulationIds) {
      jdbc.update("DELETE FROM injects WHERE inject_exercise = ?", simulationId);
      jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", simulationId);
    }
    jdbc.update("DELETE FROM injects WHERE inject_id = ?", injectId);
    jdbc.update("DELETE FROM scenarios WHERE scenario_id = ?", scenarioId);
    jdbc.update("DELETE FROM injectors_contracts WHERE injector_contract_id = ?", contractId);
    jdbc.update("DELETE FROM documents WHERE document_id = ?", documentId);
  }

  @Test
  @DisplayName("given an inject with a document attached, should answer the new simulation")
  void given_injectWithAttachedDocument_should_answerTheLaunchedSimulation() throws Exception {
    // Act & Assert
    mvc.perform(
            post(TENANT_SCENARIO_URI + "/{scenarioId}/exercise/running", TENANT, scenarioId)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exercise_injects.length()").value(1));
  }
}
