package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.MarkingDimension;
import io.openaev.config.ScopeStatementInspector;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a reader sees of a simulation's attack path, on the real tables (#8555): the run was
 * launched by a user cleared for RED and GREEN, so every row exists, and each reader gets the rows
 * of the assets its clearance covers. A row with no asset (a team, a player) is visible to
 * everyone, an execution is hidden with its asset, whichever side of the row the asset is on, and a
 * finding is visible while at least one execution that produced it is.
 *
 * <p>The marking predicate is the real one, built from the real registry of derived tables, and
 * runs on the real rows. It is applied by the inspector explicitly on a raw connection, so the
 * reader's clearance is the only scope in play (the tenant dimension is not under test).
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path: what a reader sees under marking, on the real tables")
class AttackPathMarkingRowsTest extends IntegrationTest {

  private static final String SIMULATION = "SIM-MARKING-ROWS";
  private static final String GREEN = "mk_green";
  private static final String RED = "mk_red";

  private static final String GREEN_AGENT = "mk-green-agent";
  private static final String GREEN_DISCOVERY = "mk-green-discovery";
  private static final String RED_AGENT = "mk-red-agent";
  private static final String RED_INJECTOR = "mk-red-injector";
  private static final String RED_DISCOVERY = "mk-red-discovery";
  private static final String TEAM_PLAYER = "mk-team-player";

  private static final String FINDING_GREEN = "mk-finding-green";
  private static final String FINDING_RED = "mk-finding-red";
  private static final String FINDING_MIXED = "mk-finding-mixed";
  private static final String FINDING_ORPHAN = "mk-finding-orphan";

  @Autowired private MarkingDimension markingDimension;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private EndpointComposer endpointComposer;

  private Tenant tenant;
  private Endpoint green;
  private Endpoint red;

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  private void seedAFullyLaunchedSimulation() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-marking-rows"));
    TenantContext.setCurrentTenant(tenant.getId());
    green = endpoint("green-host");
    red = endpoint("red-host");
    endpointComposer.forEndpoint(green).persist();
    endpointComposer.forEndpoint(red).persist();
    entityManager.flush();
    markAsset(green, GREEN);
    markAsset(red, RED);

    executionRepository.saveAll(
        List.of(
            agentOnItsAsset(GREEN_AGENT, green),
            agentToDiscoveredHost(GREEN_DISCOVERY, green),
            agentOnItsAsset(RED_AGENT, red),
            injectorToAsset(RED_INJECTOR, red),
            agentToDiscoveredHost(RED_DISCOVERY, red),
            teamToPlayer(TEAM_PLAYER)));
    findingRepository.saveAll(
        List.of(
            finding(FINDING_GREEN),
            finding(FINDING_RED),
            finding(FINDING_MIXED),
            finding(FINDING_ORPHAN)));
    entityManager.flush();
    link(GREEN_AGENT, FINDING_GREEN);
    link(RED_AGENT, FINDING_RED);
    link(GREEN_AGENT, FINDING_MIXED);
    link(RED_AGENT, FINDING_MIXED);
    entityManager.flush();
  }

  // -- rows --

  private Endpoint endpoint(String name) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setHostname(name);
    endpoint.setIps(new String[] {"10.0.0." + Math.abs(name.hashCode() % 200)});
    endpoint.setTenant(tenant);
    return endpoint;
  }

  private void markAsset(Endpoint endpoint, String marking) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "UPDATE assets SET marking_ids = ? WHERE asset_id = ?")) {
                statement.setArray(1, connection.createArrayOf("text", new String[] {marking}));
                statement.setString(2, endpoint.getId());
                statement.executeUpdate();
              }
            });
  }

  private AttackPathExecution row(String id) {
    AttackPathExecution row = new AttackPathExecution();
    row.setId(id);
    row.setTenant(tenant);
    row.setSimulationId(SIMULATION);
    row.setExecutedAt(Instant.now());
    return row;
  }

  private static Agent agentOn(Endpoint endpoint) {
    Agent agent = new Agent();
    agent.setId("agent-on-" + endpoint.getId());
    agent.setAsset(endpoint);
    agent.setPrivilege(Agent.PRIVILEGE.admin);
    return agent;
  }

  private AttackPathExecution agentOnItsAsset(String id, Endpoint endpoint) {
    AttackPathExecution row = row(id);
    row.setSourceAgentInformation(agentOn(endpoint), endpoint);
    row.setTargetAssetInformation(endpoint);
    return row;
  }

  private AttackPathExecution agentToDiscoveredHost(String id, Endpoint endpoint) {
    AttackPathExecution row = row(id);
    row.setSourceAgentInformation(agentOn(endpoint), endpoint);
    row.setTargetDiscoveredInformation("203.0.113." + Math.abs(id.hashCode() % 200));
    return row;
  }

  private AttackPathExecution injectorToAsset(String id, Endpoint endpoint) {
    Injector injector = new Injector();
    injector.setName("nmap");
    AttackPathExecution row = row(id);
    row.setSourceInjectorInformation(injector);
    row.setTargetAssetInformation(endpoint);
    return row;
  }

  private AttackPathExecution teamToPlayer(String id) {
    AttackPathExecution row = row(id);
    row.setSourceTeamInformation("team-1", "Blue team");
    row.setTargetPersonInformation("user-1", "player@filigran.io");
    return row;
  }

  private AttackPathFinding finding(String id) {
    AttackPathFinding finding = new AttackPathFinding();
    finding.setId(id);
    finding.setTenant(tenant);
    finding.setSimulationId(SIMULATION);
    finding.setType("Credentials");
    finding.setValue("value-of-" + id);
    finding.setEndpointKey("key-of-" + id);
    return finding;
  }

  private void link(String executionId, String findingId) {
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(executionId);
    link.setFindingId(findingId);
    entityManager.persist(link);
  }

  // -- the reader --

  /** Runs {@code sql} for a reader holding {@code clearance}, the marking predicate applied. */
  private List<String> read(String clearance, String sql) {
    List<String> values = new ArrayList<>();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement scope =
                  connection.prepareStatement(
                      "SELECT set_config('app.current_markings', ?, true)")) {
                scope.setString(1, clearance);
                scope.execute();
              }
              String inspected =
                  new ScopeStatementInspector(List.of(markingDimension)).inspect(sql);
              try (PreparedStatement statement = connection.prepareStatement(inspected);
                  ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                  values.add(result.getString(1));
                }
              }
            });
    return values;
  }

  private List<String> executionsSeenBy(String clearance) {
    return read(
        clearance,
        "SELECT ae.attackpath_execution_id FROM attackpath_execution ae"
            + " WHERE ae.attackpath_execution_simulation_id = '"
            + SIMULATION
            + "' ORDER BY 1");
  }

  private List<String> findingsSeenBy(String clearance) {
    return read(
        clearance,
        "SELECT f.attackpath_finding_id FROM attackpath_finding f"
            + " WHERE f.attackpath_finding_simulation_id = '"
            + SIMULATION
            + "' ORDER BY 1");
  }

  private List<String> linksSeenBy(String clearance) {
    return read(
        clearance,
        "SELECT l.execution_id || '>' || l.finding_id FROM attackpath_execution_finding l"
            + " WHERE l.execution_id LIKE 'mk-%' ORDER BY 1");
  }

  @Nested
  @DisplayName("a GREEN reader of a run launched by a user cleared for RED and GREEN")
  class GreenReader {

    @Test
    @DisplayName("sees the executions of the GREEN asset and of no asset, never a RED one")
    void given_greenClearance_should_seeNoRedExecution() {
      // Arrange
      seedAFullyLaunchedSimulation();

      // Act
      List<String> executions = executionsSeenBy(GREEN);

      // Assert
      assertThat(executions)
          .containsExactlyInAnyOrder(GREEN_AGENT, GREEN_DISCOVERY, TEAM_PLAYER)
          .doesNotContain(RED_AGENT, RED_INJECTOR, RED_DISCOVERY);
    }

    @Test
    @DisplayName("does not see a discovery made from a RED asset, nor an injector hit on it")
    void given_greenClearance_should_notSeeWhatWasDoneFromOrToARedAsset() {
      // Arrange
      seedAFullyLaunchedSimulation();

      // Act
      List<String> executions = executionsSeenBy(GREEN);

      // Assert
      assertThat(executions).doesNotContain(RED_DISCOVERY).doesNotContain(RED_INJECTOR);
    }

    @Test
    @DisplayName("sees a finding while one execution that produced it is visible")
    void given_greenClearance_should_seeTheFindingsOfVisibleExecutions() {
      // Arrange
      seedAFullyLaunchedSimulation();

      // Act
      List<String> findings = findingsSeenBy(GREEN);

      // Assert
      assertThat(findings)
          .containsExactlyInAnyOrder(FINDING_GREEN, FINDING_MIXED, FINDING_ORPHAN)
          .doesNotContain(FINDING_RED);
    }

    @Test
    @DisplayName("sees no link to a RED execution")
    void given_greenClearance_should_seeNoLinkToARedExecution() {
      // Arrange
      seedAFullyLaunchedSimulation();

      // Act
      List<String> links = linksSeenBy(GREEN);

      // Assert
      assertThat(links)
          .containsExactlyInAnyOrder(
              GREEN_AGENT + ">" + FINDING_GREEN, GREEN_AGENT + ">" + FINDING_MIXED);
    }
  }

  @Nested
  @DisplayName("a RED and GREEN reader")
  class RedAndGreenReader {

    @Test
    @DisplayName("sees every execution, finding and link")
    void given_fullClearance_should_seeEverything() {
      // Arrange
      seedAFullyLaunchedSimulation();

      // Act / Assert
      assertThat(executionsSeenBy(GREEN + "," + RED))
          .containsExactlyInAnyOrder(
              GREEN_AGENT, GREEN_DISCOVERY, RED_AGENT, RED_INJECTOR, RED_DISCOVERY, TEAM_PLAYER);
      assertThat(findingsSeenBy(GREEN + "," + RED))
          .containsExactlyInAnyOrder(FINDING_GREEN, FINDING_RED, FINDING_MIXED, FINDING_ORPHAN);
      assertThat(linksSeenBy(GREEN + "," + RED)).hasSize(4);
    }
  }

  @Nested
  @DisplayName("a reader with no clearance")
  class NoClearanceReader {

    @Test
    @DisplayName("sees only what is attached to no marked asset")
    void given_noClearance_should_seeOnlyWhatHasNoMarkedAsset() {
      // Arrange
      seedAFullyLaunchedSimulation();

      // Act / Assert
      assertThat(executionsSeenBy("")).containsExactly(TEAM_PLAYER);
      assertThat(findingsSeenBy("")).containsExactly(FINDING_ORPHAN);
      assertThat(linksSeenBy("")).isEmpty();
    }
  }
}
