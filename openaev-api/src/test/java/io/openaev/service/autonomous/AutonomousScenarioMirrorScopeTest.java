package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * An autonomous run mirrors every step it authors on its simulation onto its scenario, in a {@code
 * REQUIRES_NEW} transaction so a mirror failure cannot roll back the executing step. A new
 * transaction does not inherit the caller's tenant scope, so the mirror must carry the run's own.
 * Without it, every read of a tenant-active table inside the mirror returns nothing: the asset
 * targets and tags of the twin are written empty, and once {@code injectors_contracts} is active
 * the contract check refuses the twin altogether.
 *
 * <p>The tests arm the production list plus {@code injectors_contracts}, seed a tenant of their own
 * through auto-committed JDBC, and call the service inside a scoped transaction, the shape of the
 * orchestrator callback handler. Not {@code @Transactional}: the mirror's own transaction could not
 * see rows left uncommitted in a test-managed one.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Autonomous run scenario mirror under the run's tenant scope")
class AutonomousScenarioMirrorScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTablesWithInjectorContracts(DynamicPropertyRegistry registry) {
    TreeSet<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("injectors_contracts");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
  }

  @Autowired private AutonomousRunService autonomousRunService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private final ObjectMapper mapper = new ObjectMapper();
  private JdbcTemplate jdbc;
  private String tenantId;
  private String contractId;
  private String assetId;
  private String otherAssetId;
  private String tagId;
  private String exerciseId;
  private String scenarioId;
  private String runId;
  private String teamId;
  private String memberId;
  private final List<String> workflowIds = new ArrayList<>();
  private Logger serviceLogger;
  private Level previousLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void seedARunWithItsSimulationAndScenario() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    contractId = UUID.randomUUID().toString();
    assetId = UUID.randomUUID().toString();
    otherAssetId = UUID.randomUUID().toString();
    tagId = UUID.randomUUID().toString();
    exerciseId = UUID.randomUUID().toString();
    scenarioId = UUID.randomUUID().toString();
    runId = UUID.randomUUID().toString();
    teamId = UUID.randomUUID().toString();
    memberId = UUID.randomUUID().toString();
    workflowIds.clear();

    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "autonomous-mirror-scope-" + tenantId);
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " tenant_id) VALUES (?, ?, ?)",
        contractId,
        "{\"fields\":[{\"key\":\"assets\",\"type\":\"asset\"}]}",
        tenantId);
    seedEndpoint(assetId);
    seedEndpoint(otherAssetId);
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        tagId,
        "autonomous-mirror-" + tagId,
        tenantId);
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " tenant_id) VALUES (?, 'autonomous-mirror', 'mirror@openaev.invalid',"
            + " 'RUNNING', ?)",
        exerciseId,
        tenantId);
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
            + " VALUES (?, 'autonomous-mirror', 'mirror@openaev.invalid', ?)",
        scenarioId,
        tenantId);
    seedTemplate("workflow_simulation_id", exerciseId);
    seedTemplate("workflow_scenario_id", scenarioId);
    jdbc.update(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_simulation_id, autonomous_run_scenario_id)"
            + " VALUES (?, ?, 'mirror', 'RUNNING', ?, ?)",
        runId,
        tenantId,
        exerciseId,
        scenarioId);
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, 'autonomous-mirror', ?)",
        teamId,
        tenantId);
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        memberId,
        "autonomous-mirror-" + memberId + "@openaev.invalid");
    jdbc.update("INSERT INTO users_teams (team_id, user_id) VALUES (?, ?)", teamId, memberId);
    TenantContext.clearCurrentTenant();

    serviceLogger = (Logger) LoggerFactory.getLogger(AutonomousRunService.class);
    // The test profile logs at ERROR only
    previousLevel = serviceLogger.getLevel();
    serviceLogger.setLevel(Level.WARN);
    appender = new ListAppender<>();
    appender.start();
    serviceLogger.addAppender(appender);
  }

  @AfterEach
  void sweep() {
    serviceLogger.detachAppender(appender);
    serviceLogger.setLevel(previousLevel);
    jdbc.update("DELETE FROM autonomous_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM exercises_teams_users WHERE exercise_id = ?", exerciseId);
    jdbc.update("DELETE FROM exercises_teams WHERE exercise_id = ?", exerciseId);
    jdbc.update("DELETE FROM users_teams WHERE team_id = ?", teamId);
    jdbc.update("DELETE FROM teams WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", memberId);
    jdbc.update("DELETE FROM autonomous_runs WHERE tenant_id = ?", tenantId);
    // Deleting the workflows cascades to their steps and conditions
    jdbc.update("DELETE FROM workflows WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM scenarios WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tags WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injectors_contracts WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Authoring a step")
  class Append {

    @Test
    @DisplayName("the scenario twin is created with the contract, asset targets and tags")
    void given_authoredStep_should_mirrorItOntoScenarioWithItsTargets() throws Exception {
      // Act
      String simStepId = author(input(assetId));

      // Assert - the executing step first, so a failure below is about the mirror alone
      assertThat(targetsOf(simStepId)).containsExactly(assetId);
      String twinId = twinOf(simStepId);
      assertThat(twinId)
          .as("the run must record a scenario twin for the authored step")
          .isNotBlank();
      JsonNode twin = stepData(twinId);
      assertThat(twin.path("inject_injector_contract").toString()).contains(contractId);
      assertThat(targetsOf(twinId))
          .as("the twin must keep the asset targets the executing step has")
          .containsExactly(assetId);
      assertThat(ids(twin.path("inject_tags")))
          .as("the twin must keep the tags the executing step has")
          .containsExactly(tagId);
    }

    @Test
    @DisplayName("a mirror failure keeps the executing step and logs its cause")
    void given_mirrorFailure_should_keepExecutingStepAndLogTheCause() {
      // Arrange - no scenario workflow, so the mirror cannot find where to append
      jdbc.update("DELETE FROM workflows WHERE workflow_scenario_id = ?", scenarioId);

      // Act
      String simStepId = author(input(assetId));

      // Assert
      assertThat(targetsOf(simStepId)).containsExactly(assetId);
      assertThat(twinOf(simStepId)).isNull();
      assertThat(appender.list)
          .filteredOn(e -> e.getLevel() == Level.WARN)
          .filteredOn(e -> e.getFormattedMessage().contains("Failed to mirror"))
          .singleElement()
          .satisfies(
              e ->
                  assertThat(e.getThrowableProxy())
                      .as("the warning must carry the exception, not only its message")
                      .isNotNull());
    }
  }

  @Nested
  @DisplayName("Updating a step")
  class Update {

    @Test
    @DisplayName("the scenario twin follows the new asset targets")
    void given_updatedStep_should_updateTheTwinTargets() throws Exception {
      // Arrange
      String simStepId = author(input(assetId));
      String twinId = twinOf(simStepId);
      assertThat(twinId).isNotBlank();

      // Act
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> {
            autonomousRunService.updateAttackPathStep(runId, simStepId, input(otherAssetId), null);
          });

      // Assert
      assertThat(targetsOf(simStepId)).containsExactly(otherAssetId);
      assertThat(targetsOf(twinId))
          .as("the twin must follow the executing step's new targets")
          .containsExactly(otherAssetId);
    }

    @Test
    @DisplayName("a twin update failure keeps the executing step update and logs its cause")
    void given_twinUpdateFailure_should_keepExecutingStepUpdateAndLogTheCause() {
      // Arrange - the twin is gone, so updating it fails
      String simStepId = author(input(assetId));
      String twinId = twinOf(simStepId);
      assertThat(twinId).isNotBlank();
      jdbc.update("DELETE FROM steps WHERE step_id = ?", twinId);

      // Act
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> {
            autonomousRunService.updateAttackPathStep(runId, simStepId, input(otherAssetId), null);
          });

      // Assert
      assertThat(targetsOf(simStepId)).containsExactly(otherAssetId);
      assertThat(appender.list)
          .filteredOn(e -> e.getLevel() == Level.WARN)
          .filteredOn(e -> e.getFormattedMessage().contains("Failed to update scenario mirror"))
          .singleElement()
          .satisfies(
              e ->
                  assertThat(e.getThrowableProxy())
                      .as("the warning must carry the exception, not only its message")
                      .isNotNull());
    }
  }

  @Nested
  @DisplayName("Enabling the members of a targeted team")
  class TeamMembers {

    @Test
    @DisplayName("authoring a step that targets a team enables its members on the simulation")
    void given_stepTargetingTeam_should_enableItsMembersOnSimulation() {
      // Arrange
      InjectInput input = input(assetId);
      input.setTeams(List.of(teamId));

      // Act
      author(input);

      // Assert
      assertThat(enabledMembers())
          .as("the team's member must be enabled, or a human-targeted step has nobody to reach")
          .containsExactly(memberId);
    }

    @Test
    @DisplayName("recording a scope that holds a team enables its members on the simulation")
    void given_scopeHoldingTeam_should_enableItsMembersOnSimulation() {
      // Arrange
      AutonomousScopeTarget target = new AutonomousScopeTarget();
      target.setType("TEAMS");
      target.setId(teamId);

      // Act
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> {
            autonomousRunService.setRunScope(runId, List.of(target));
          });

      // Assert
      assertThat(enabledMembers()).containsExactly(memberId);
    }
  }

  // -- helpers --

  private List<String> enabledMembers() {
    return jdbc.queryForList(
        "SELECT user_id FROM exercises_teams_users WHERE exercise_id = ? AND team_id = ?",
        String.class,
        exerciseId,
        teamId);
  }

  private String author(InjectInput input) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> autonomousRunService.appendAttackPathStep(runId, input, null));
  }

  private InjectInput input(String targetAssetId) {
    InjectInput input = new InjectInput();
    input.setTitle("autonomous-mirror-step");
    input.setInjectorContract(contractId);
    input.setAssets(List.of(targetAssetId));
    input.setTagIds(List.of(tagId));
    return input;
  }

  private void seedEndpoint(String id) {
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        id,
        "autonomous-mirror-" + id,
        tenantId,
        "autonomous-mirror-" + id);
  }

  private void seedTemplate(String ownerColumn, String ownerId) {
    String workflowId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version, "
            + ownerColumn
            + ", tenant_id) VALUES (?, 'TEMPLATE', 0, ?, ?)",
        workflowId,
        ownerId,
        tenantId);
    workflowIds.add(workflowId);
  }

  private String twinOf(String simStepId) {
    String mirror =
        jdbc.queryForObject(
            "SELECT autonomous_run_step_mirror::text FROM autonomous_runs"
                + " WHERE autonomous_run_id = ?",
            String.class,
            runId);
    if (mirror == null) {
      return null;
    }
    try {
      JsonNode twin = mapper.readTree(mirror).get(simStepId);
      return twin == null ? null : twin.asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode stepData(String stepId) throws Exception {
    String data =
        jdbc.queryForObject(
            "SELECT step_data::text FROM steps WHERE step_id = ?", String.class, stepId);
    return mapper.readTree(data);
  }

  private List<String> targetsOf(String stepId) {
    try {
      return ids(stepData(stepId).path("inject_assets"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> ids(JsonNode array) {
    List<String> ids = new ArrayList<>();
    array.forEach(n -> ids.add(n.asText()));
    return ids;
  }
}
