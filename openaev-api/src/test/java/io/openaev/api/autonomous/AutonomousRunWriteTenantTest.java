package io.openaev.api.autonomous;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.security.token.XtmJwksExtractor;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The orchestrator callbacks that record a run's scope and author, update and delete its steps, and
 * the create of a run, write in the run's own tenant on every route: the route that names no tenant
 * with {@code X-Tenant-Ids}, the same route as the verified XTM One service identity, and the
 * tenant-prefixed route. The thread carries no ambient tenant before each request, the state of a
 * pooled request thread on the route that names no tenant. Every row lands in the run's tenant,
 * nothing lands in the default tenant, and every stream event of the request carries the run's
 * tenant.
 *
 * <p>Not {@code @Transactional}: the scope mirror and the step mirror run in their own {@code
 * REQUIRES_NEW} transactions, which do not see rows left uncommitted in a test-managed one. The
 * rows are committed through JDBC in a tenant of their own and removed after each test.
 */
@WithMockUser(isAdmin = true)
@RecordApplicationEvents
@DisplayName("autonomous run writes land in the run's tenant on every route")
class AutonomousRunWriteTenantTest extends IntegrationTest {

  private static final String PLAIN = AUTONOMOUS_URI;
  private static final String SCOPED = TENANT_PREFIX + "/autonomous-runs";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ApplicationEvents applicationEvents;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String contractId;
  private String assetId;
  private String exerciseId;
  private String scenarioId;
  private String runId;
  private String teamId;
  private String memberId;

  @BeforeEach
  void seedARunOfItsOwnTenantWithItsSimulationAndScenario() {
    // Arrange
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    contractId = UUID.randomUUID().toString();
    assetId = UUID.randomUUID().toString();
    exerciseId = UUID.randomUUID().toString();
    scenarioId = UUID.randomUUID().toString();
    runId = UUID.randomUUID().toString();
    teamId = UUID.randomUUID().toString();
    memberId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "autonomous-write-tenant-" + tenantId);
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " tenant_id) VALUES (?, ?, ?)",
        contractId,
        "{\"fields\":[{\"key\":\"assets\",\"type\":\"asset\"}]}",
        tenantId);
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        assetId,
        "autonomous-write-tenant-" + assetId,
        tenantId,
        "autonomous-write-tenant-" + assetId);
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " VALUES (?, 'autonomous-write-tenant', 'RUNNING', ?)",
        exerciseId,
        tenantId);
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id)"
            + " VALUES (?, 'autonomous-write-tenant', ?)",
        scenarioId,
        tenantId);
    seedTemplateWorkflow("workflow_simulation_id", exerciseId);
    seedTemplateWorkflow("workflow_scenario_id", scenarioId);
    jdbc.update(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_simulation_id, autonomous_run_scenario_id)"
            + " VALUES (?, ?, 'write tenant', 'RUNNING', ?, ?)",
        runId,
        tenantId,
        exerciseId,
        scenarioId);
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, 'autonomous-write', ?)",
        teamId,
        tenantId);
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        memberId,
        "autonomous-write-tenant-" + memberId + "@openaev.invalid");
    jdbc.update("INSERT INTO users_teams (team_id, user_id) VALUES (?, ?)", teamId, memberId);
    tenantHelper.attachCurrentUserToTenant(tenantId);
    clearAmbientTenant();
    // The membership written above publishes events of its own, outside any request under test.
    applicationEvents.clear();
  }

  @AfterEach
  void removeTheTenantAndWhatItOwns() {
    TenantContext.clearCurrentTenant();
    jdbc.update("DELETE FROM users_teams WHERE team_id = ?", teamId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", memberId);
    // Every foreign key to tenants cascades: the delete removes the rows the tenant owns, the
    // workflows with their steps and conditions, and the caller's membership.
    jdbc.update(
        "DELETE FROM exercises_teams_users WHERE exercise_id IN"
            + " (SELECT exercise_id FROM exercises WHERE tenant_id = ?)",
        tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  @Nested
  @DisplayName("route without a tenant prefix, X-Tenant-Ids naming the run's tenant")
  class HeaderRoute {

    @Test
    @DisplayName("given_scopeHoldingATeam_should_recordItInTheRunTenant")
    void given_scopeHoldingATeam_should_recordItInTheRunTenant() throws Exception {
      // Act
      mvc.perform(onHeaderRoute(setScope(PLAIN + "/{runId}/scope", runId)))
          .andExpect(status().isOk());

      // Assert
      assertThat(
              tenantsOf(
                  "SELECT tenant_id FROM workflow_scope_rules WHERE workflow_id IN"
                      + " (SELECT workflow_id FROM workflows WHERE workflow_scenario_id = ?)",
                  scenarioId))
          .isNotEmpty()
          .containsOnly(tenantId);
      assertThat(enabledMembers()).containsExactly(memberId);
      assertRunEventsInRunTenant();
      assertStreamEventsInRunTenant(true);
    }

    @Test
    @DisplayName("given_newStep_should_writeItAndItsTwinInTheRunTenant")
    void given_newStep_should_writeItAndItsTwinInTheRunTenant() throws Exception {
      // Act
      String stepId = appendStep(onHeaderRoute(post(PLAIN + "/{runId}/attack-path/steps", runId)));

      // Assert
      assertThat(tenantOfStep(stepId)).isEqualTo(tenantId);
      assertThat(tenantsOfScenarioSteps()).containsExactly(tenantId);
      assertThat(enabledMembers()).containsExactly(memberId);
      assertRunEventsInRunTenant();
      assertStreamEventsInRunTenant(false);
    }

    @Test
    @DisplayName("given_authoredStep_should_updateItAndItsTwinInTheRunTenant")
    void given_authoredStep_should_updateItAndItsTwinInTheRunTenant() throws Exception {
      // Arrange
      String stepId = appendStep(onHeaderRoute(post(PLAIN + "/{runId}/attack-path/steps", runId)));
      clearAmbientTenant();
      applicationEvents.clear();

      // Act
      mvc.perform(
              onHeaderRoute(
                  stepBody(put(PLAIN + "/{runId}/attack-path/steps/{stepId}", runId, stepId))))
          .andExpect(status().isOk());

      // Assert
      assertThat(tenantOfStep(stepId)).isEqualTo(tenantId);
      assertThat(tenantsOfScenarioSteps()).containsExactly(tenantId);
      assertRunEventsInRunTenant();
      assertStreamEventsInRunTenant(false);
    }

    @Test
    @DisplayName("given_authoredStep_should_deleteItAndItsTwinInTheRunTenant")
    void given_authoredStep_should_deleteItAndItsTwinInTheRunTenant() throws Exception {
      // Arrange
      String stepId = appendStep(onHeaderRoute(post(PLAIN + "/{runId}/attack-path/steps", runId)));
      clearAmbientTenant();
      applicationEvents.clear();

      // Act
      mvc.perform(
              onHeaderRoute(
                  delete(PLAIN + "/{runId}/attack-path/steps/{stepId}", runId, stepId)
                      .with(csrf())))
          .andExpect(status().isOk());

      // Assert
      assertThat(stepCount(stepId)).isZero();
      assertThat(tenantsOfScenarioSteps()).isEmpty();
      assertRunEventsInRunTenant();
      assertStreamEventsInRunTenant(false);
    }

    @Test
    @DisplayName("given_objectiveOnly_should_createTheRunAndItsSimulationInTheNamedTenant")
    void given_objectiveOnly_should_createTheRunAndItsSimulationInTheNamedTenant()
        throws Exception {
      // Act
      String response =
          mvc.perform(
                  onHeaderRoute(
                      post(PLAIN)
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              "{\"objective\": \"autonomous-write-tenant-create\","
                                  + " \"plan_mode\": true}")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String createdRunId = JsonPath.read(response, "$.autonomous_run_id");
      String simulationId = JsonPath.read(response, "$.autonomous_run_simulation_id");
      String createdScenarioId = JsonPath.read(response, "$.autonomous_run_scenario_id");
      assertThat(
              tenantsOf(
                  "SELECT tenant_id FROM autonomous_runs WHERE autonomous_run_id = ?",
                  createdRunId))
          .containsExactly(tenantId);
      assertThat(tenantsOf("SELECT tenant_id FROM exercises WHERE exercise_id = ?", simulationId))
          .containsExactly(tenantId);
      assertThat(
              tenantsOf("SELECT tenant_id FROM scenarios WHERE scenario_id = ?", createdScenarioId))
          .containsExactly(tenantId);
      assertThat(
              tenantsOf(
                  "SELECT tenant_id FROM autonomous_events WHERE autonomous_event_run_id = ?",
                  createdRunId))
          .isNotEmpty()
          .containsOnly(tenantId);
      assertStreamEventsInRunTenant(true);
    }
  }

  @Nested
  @DisplayName("route without a tenant prefix, verified XTM One service identity")
  class ServiceIdentityRoute {

    @Test
    @DisplayName("given_newStep_should_writeItAndItsTwinInTheRunTenant")
    void given_newStep_should_writeItAndItsTwinInTheRunTenant() throws Exception {
      // Act
      String stepId =
          appendStep(
              post(PLAIN + "/{runId}/attack-path/steps", runId)
                  .requestAttr(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE));

      // Assert
      assertThat(tenantOfStep(stepId)).isEqualTo(tenantId);
      assertThat(tenantsOfScenarioSteps()).containsExactly(tenantId);
      assertRunEventsInRunTenant();
      assertStreamEventsInRunTenant(false);
    }
  }

  @Nested
  @DisplayName("tenant-prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName("given_scopeHoldingATeam_should_recordItInTheRunTenant")
    void given_scopeHoldingATeam_should_recordItInTheRunTenant() throws Exception {
      // Act
      mvc.perform(setScope(SCOPED + "/{runId}/scope", tenantId, runId)).andExpect(status().isOk());

      // Assert
      assertThat(enabledMembers()).containsExactly(memberId);
      assertRunEventsInRunTenant();
      assertStreamEventsInRunTenant(true);
    }

    @Test
    @DisplayName("given_newStep_should_writeItAndItsTwinInTheRunTenant")
    void given_newStep_should_writeItAndItsTwinInTheRunTenant() throws Exception {
      // Act
      String stepId = appendStep(post(SCOPED + "/{runId}/attack-path/steps", tenantId, runId));

      // Assert
      assertThat(tenantOfStep(stepId)).isEqualTo(tenantId);
      assertThat(tenantsOfScenarioSteps()).containsExactly(tenantId);
      assertRunEventsInRunTenant();
      assertStreamEventsInRunTenant(false);
    }
  }

  // -- requests --

  private MockHttpServletRequestBuilder onHeaderRoute(MockHttpServletRequestBuilder request) {
    return request.header(TENANT_IDS_HEADER, tenantId);
  }

  private MockHttpServletRequestBuilder setScope(String uri, Object... uriVariables) {
    return put(uri, uriVariables)
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"scope\": [{\"type\": \"TEAMS\", \"id\": \"" + teamId + "\"}]}");
  }

  private MockHttpServletRequestBuilder stepBody(MockHttpServletRequestBuilder request) {
    return request
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"inject\": {\"inject_title\": \"autonomous-write-tenant-step\","
                + " \"inject_injector_contract\": \""
                + contractId
                + "\", \"inject_assets\": [\""
                + assetId
                + "\"], \"inject_teams\": [\""
                + teamId
                + "\"]}}");
  }

  private String appendStep(MockHttpServletRequestBuilder request) throws Exception {
    String response =
        mvc.perform(stepBody(request))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.step_template_id");
  }

  // -- assertions --

  // The route without a prefix sets no ambient tenant; a fixture may have left one on the thread.
  private void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private void assertRunEventsInRunTenant() {
    assertThat(
            tenantsOf(
                "SELECT tenant_id FROM autonomous_events WHERE autonomous_event_run_id = ?", runId))
        .isNotEmpty()
        .containsOnly(tenantId);
  }

  /**
   * Every stream event the request built carries the run's tenant. When {@code expectSome} is set,
   * the request writes a streamed row, so an empty capture would make the check vacuous.
   */
  private void assertStreamEventsInRunTenant(boolean expectSome) {
    List<String> eventTenants =
        applicationEvents.stream(BaseEvent.class).map(BaseEvent::getTenantId).toList();
    if (expectSome) {
      assertThat(eventTenants).isNotEmpty();
    }
    assertThat(eventTenants).allMatch(tenantId::equals);
  }

  private List<String> enabledMembers() {
    return jdbc.queryForList(
        "SELECT user_id FROM exercises_teams_users WHERE exercise_id = ? AND team_id = ?",
        String.class,
        exerciseId,
        teamId);
  }

  private String tenantOfStep(String stepId) {
    return jdbc.queryForObject(
        "SELECT tenant_id FROM steps WHERE step_id = ?", String.class, stepId);
  }

  private int stepCount(String stepId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM steps WHERE step_id = ?", Integer.class, stepId);
  }

  private List<String> tenantsOfScenarioSteps() {
    return tenantsOf(
        "SELECT s.tenant_id FROM steps s JOIN workflows w ON w.workflow_id = s.step_workflow_id"
            + " WHERE w.workflow_scenario_id = ?",
        scenarioId);
  }

  private List<String> tenantsOf(String sql, String id) {
    return jdbc.queryForList(sql, String.class, id);
  }

  private void seedTemplateWorkflow(String ownerColumn, String ownerId) {
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version, "
            + ownerColumn
            + ", tenant_id) VALUES (?, 'TEMPLATE', 0, ?, ?)",
        UUID.randomUUID().toString(),
        ownerId,
        tenantId);
  }
}
