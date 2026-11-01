package io.openaev.rest.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.ProductionActiveTables;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Simulation and scenario by-id reads resolve the row inside the request's tenant scope, with the
 * production list of isolated tables ({@code exercises} and {@code scenarios} are not in it). On
 * the tenant route the scope is the path tenant, so nothing changes there. On the non-prefixed
 * route the scope is the {@code X-Tenant-Ids} selector, or the caller's memberships when it names
 * none: a row outside that scope is not found by id.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Simulation and scenario by-id reads in the request scope, production tables")
class ByIdRequestScopeTest extends ByIdRequestScopeTestBase {

  // The autonomous launch is EE-gated; the mock's license checks default to "active".
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Nested
  @DisplayName("Tenant route")
  class TenantRoute {

    @Test
    @DisplayName("given a simulation of a member tenant, it is read, updated and deleted")
    void given_simulationOfMemberTenant_should_beReadUpdatedAndDeleted() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      Route route = tenantRoute(memberTenant);

      // Act
      int read = readSimulation(route, simulationId);
      int update = updateSimulation(route, simulationId);
      int delete = deleteSimulation(route, simulationId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(200, 200, 200);
      assertThat(simulationExists(simulationId)).isFalse();
    }

    @Test
    @DisplayName("given a scenario of a member tenant, it is read, updated and deleted")
    void given_scenarioOfMemberTenant_should_beReadUpdatedAndDeleted() throws Exception {
      // Arrange
      String scenarioId = seedScenario(memberTenant);
      Route route = tenantRoute(memberTenant);

      // Act
      int read = readScenario(route, scenarioId);
      int update = updateScenario(route, scenarioId);
      int delete = deleteScenario(route, scenarioId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(200, 200, 200);
      assertThat(scenarioExists(scenarioId)).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given default tenant rows on the route of another tenant, their teams are not removed")
    void given_defaultTenantRowsOnRouteOfAnotherTenant_should_notRemoveTheirTeams()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String simulationTeamId = seedTeamOfSimulation(DEFAULT_TENANT, simulationId);
      String scenarioId = seedScenario(DEFAULT_TENANT);
      String scenarioTeamId = seedTeamOfScenario(DEFAULT_TENANT, scenarioId);
      Route route = tenantRoute(memberTenant);

      // Act
      int simulationRemove = removeSimulationTeam(route, simulationId, simulationTeamId);
      int scenarioRemove = removeScenarioTeam(route, scenarioId, scenarioTeamId);

      // Assert
      assertThat(new int[] {simulationRemove, scenarioRemove}).containsExactly(404, 404);
      assertThat(simulationHasTeam(simulationId, simulationTeamId)).isTrue();
      assertThat(scenarioHasTeam(scenarioId, scenarioTeamId)).isTrue();
    }
  }

  @Nested
  @DisplayName("Non-prefixed route")
  class NonPrefixedRoute {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant simulation and a selector naming another tenant, it is not found"
            + " by id")
    void given_defaultTenantSimulationAndSelectorNamingAnotherTenant_should_notBeFound()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      Route route = headerRoute(memberTenant);

      // Act
      int read = readSimulation(route, simulationId);
      int update = updateSimulation(route, simulationId);
      int delete = deleteSimulation(route, simulationId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(404, 404, 404);
      assertThat(simulationExists(simulationId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and a selector naming another tenant, it is not found"
            + " by id")
    void given_defaultTenantScenarioAndSelectorNamingAnotherTenant_should_notBeFound()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      Route route = headerRoute(memberTenant);

      // Act
      int read = readScenario(route, scenarioId);
      int update = updateScenario(route, scenarioId);
      int delete = deleteScenario(route, scenarioId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(404, 404, 404);
      assertThat(scenarioExists(scenarioId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant simulation and no selector, a member of the default tenant still"
            + " reads, updates and deletes it")
    void given_defaultTenantSimulationAndNoSelector_should_stayReachableForDefaultMember()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      Route route = noSelectorRoute();

      // Act
      int read = readSimulation(route, simulationId);
      int update = updateSimulation(route, simulationId);
      int delete = deleteSimulation(route, simulationId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(200, 200, 200);
      assertThat(simulationExists(simulationId)).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and no selector, a member of the default tenant still"
            + " reads, updates and deletes it")
    void given_defaultTenantScenarioAndNoSelector_should_stayReachableForDefaultMember()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      Route route = noSelectorRoute();

      // Act
      int read = readScenario(route, scenarioId);
      int update = updateScenario(route, scenarioId);
      int delete = deleteScenario(route, scenarioId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(200, 200, 200);
      assertThat(scenarioExists(scenarioId)).isFalse();
    }

    @Test
    @DisplayName(
        "given a caller with no membership and no selector, a default tenant simulation and"
            + " scenario stay reachable by id while the two tables keep the v1 filter")
    void given_callerWithNoMembershipAndNoSelector_should_reachDefaultTenantRowsUntilIsolation()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String scenarioId = seedScenario(DEFAULT_TENANT);
      leaveMemberTenant();
      Route route = noSelectorRoute();

      // Act
      int simulationUpdate = updateSimulation(route, simulationId);
      int scenarioUpdate = updateScenario(route, scenarioId);

      // Assert
      assertThat(new int[] {simulationUpdate, scenarioUpdate}).containsExactly(200, 200);
    }

    @Test
    @DisplayName("given a selector naming a tenant the caller is not a member of, it is refused")
    void given_selectorNamingNonMemberTenant_should_beRefused() throws Exception {
      // Arrange
      String simulationId = seedSimulation(otherTenant);
      String scenarioId = seedScenario(otherTenant);
      Route route = headerRoute(otherTenant);

      // Act
      int simulationDelete = deleteSimulation(route, simulationId);
      int scenarioDelete = deleteScenario(route, scenarioId);

      // Assert
      assertThat(new int[] {simulationDelete, scenarioDelete}).containsExactly(403, 403);
      assertThat(simulationExists(simulationId)).isTrue();
      assertThat(scenarioExists(scenarioId)).isTrue();
    }
  }

  @Nested
  @DisplayName("Non-prefixed route, endpoints that act on a simulation or scenario by id")
  class EndpointsByIdOnNonPrefixedRoute {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant simulation and a selector naming another tenant, every simulation"
            + " endpoint answers not found and changes nothing")
    void given_defaultTenantSimulationAndSelectorNamingAnotherTenant_should_refuseEveryEndpoint()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String teamId = seedTeamOfSimulation(DEFAULT_TENANT, simulationId);
      linkSimulationToScenario(simulationId, seedScenario(DEFAULT_TENANT));
      int simulationsBefore = simulationCount();
      Route route = headerRoute(memberTenant);

      // Act
      int read = getSimulation(route, simulationId);
      int teams = getSimulationTeams(route, simulationId);
      int results = getSimulationResultsByAttackPattern(route, simulationId);
      int scenario = getScenarioOfSimulation(route, simulationId);
      int duplicate = duplicateSimulation(route, simulationId);
      int healthChecks = getSimulationHealthChecks(route, simulationId);
      int players = getSimulationPlayers(route, simulationId);
      int logs = getSimulationLogs(route, simulationId);
      int comchecks = getSimulationComchecks(route, simulationId);
      int removeTeam = removeSimulationTeam(route, simulationId, teamId);

      // Assert
      assertThat(
              new int[] {
                read,
                teams,
                results,
                scenario,
                duplicate,
                healthChecks,
                players,
                logs,
                comchecks,
                removeTeam
              })
          .containsExactly(404, 404, 404, 404, 404, 404, 404, 404, 404, 404);
      assertThat(simulationCount()).isEqualTo(simulationsBefore);
      assertThat(simulationHasTeam(simulationId, teamId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant simulation and no selector, a member of the default tenant still"
            + " reaches every simulation endpoint")
    void given_defaultTenantSimulationAndNoSelector_should_reachEveryEndpoint() throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String teamId = seedTeamOfSimulation(DEFAULT_TENANT, simulationId);
      linkSimulationToScenario(simulationId, seedScenario(DEFAULT_TENANT));
      int simulationsBefore = simulationCount();
      Route route = noSelectorRoute();

      // Act
      int read = getSimulation(route, simulationId);
      int teams = getSimulationTeams(route, simulationId);
      int results = getSimulationResultsByAttackPattern(route, simulationId);
      int scenario = getScenarioOfSimulation(route, simulationId);
      int duplicate = duplicateSimulation(route, simulationId);
      int healthChecks = getSimulationHealthChecks(route, simulationId);
      int players = getSimulationPlayers(route, simulationId);
      int logs = getSimulationLogs(route, simulationId);
      int comchecks = getSimulationComchecks(route, simulationId);
      int removeTeam = removeSimulationTeam(route, simulationId, teamId);

      // Assert
      assertThat(
              new int[] {
                read,
                teams,
                results,
                scenario,
                duplicate,
                healthChecks,
                players,
                logs,
                comchecks,
                removeTeam
              })
          .containsExactly(200, 200, 200, 200, 200, 200, 200, 200, 200, 200);
      assertThat(simulationCount()).isEqualTo(simulationsBefore + 1);
      assertThat(simulationHasTeam(simulationId, teamId)).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and a selector naming another tenant, every scenario"
            + " endpoint answers not found and changes nothing")
    void given_defaultTenantScenarioAndSelectorNamingAnotherTenant_should_refuseEveryEndpoint()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      String teamId = seedTeamOfScenario(DEFAULT_TENANT, scenarioId);
      Route route = headerRoute(memberTenant);

      // Act
      int read = getScenario(route, scenarioId);
      int teams = getScenarioTeams(route, scenarioId);
      int launch = launchAutonomousRun(route, scenarioId);
      int healthChecks = getScenarioHealthChecks(route, scenarioId);
      int players = getScenarioPlayers(route, scenarioId);
      int removeTeam = removeScenarioTeam(route, scenarioId, teamId);

      // Assert
      assertThat(new int[] {read, teams, launch, healthChecks, players, removeTeam})
          .containsExactly(404, 404, 404, 404, 404, 404);
      assertThat(autonomousRunCount(scenarioId)).isZero();
      assertThat(scenarioHasTeam(scenarioId, teamId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and no selector, a member of the default tenant still"
            + " reaches every scenario endpoint")
    void given_defaultTenantScenarioAndNoSelector_should_reachEveryEndpoint() throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      String teamId = seedTeamOfScenario(DEFAULT_TENANT, scenarioId);
      Route route = noSelectorRoute();

      // Act
      int read = getScenario(route, scenarioId);
      int teams = getScenarioTeams(route, scenarioId);
      // Reached: refused only because the seeded scenario has no attack path workflow.
      int launch = launchAutonomousRun(route, scenarioId);
      int healthChecks = getScenarioHealthChecks(route, scenarioId);
      int players = getScenarioPlayers(route, scenarioId);
      int removeTeam = removeScenarioTeam(route, scenarioId, teamId);

      // Assert
      assertThat(new int[] {read, teams, launch, healthChecks, players, removeTeam})
          .containsExactly(200, 200, 400, 200, 200, 200);
      assertThat(scenarioHasTeam(scenarioId, teamId)).isFalse();
    }
  }

  @Nested
  @DisplayName("Scenario duplicate and bulk deletes outside the request scope")
  class DuplicateAndBulkDeleteOutsideTheScope {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and a selector naming another tenant, its duplicate"
            + " answers not found and copies nothing")
    void given_defaultTenantScenarioAndSelectorNamingAnotherTenant_should_notDuplicateIt()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      int scenariosBefore = scenarioCount();

      // Act
      int duplicate = duplicateScenario(headerRoute(memberTenant), scenarioId);

      // Assert
      assertThat(duplicate).isEqualTo(404);
      assertThat(scenarioCount()).isEqualTo(scenariosBefore);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario on the route of another member tenant, its duplicate"
            + " answers not found and copies nothing")
    void given_defaultTenantScenarioOnRouteOfAnotherTenant_should_notDuplicateIt()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      int scenariosBefore = scenarioCount();

      // Act
      int duplicate = duplicateScenario(tenantRoute(memberTenant), scenarioId);

      // Assert
      assertThat(duplicate).isEqualTo(404);
      assertThat(scenarioCount()).isEqualTo(scenariosBefore);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and no selector, a member of the default tenant"
            + " duplicates it")
    void given_defaultTenantScenarioAndNoSelector_should_duplicateIt() throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      int scenariosBefore = scenarioCount();

      // Act
      int duplicate = duplicateScenario(noSelectorRoute(), scenarioId);

      // Assert
      assertThat(duplicate).isEqualTo(200);
      assertThat(scenarioCount()).isEqualTo(scenariosBefore + 1);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and a selector naming another tenant, the bulk delete"
            + " answers not found and keeps it")
    void given_defaultTenantScenarioAndSelectorNamingAnotherTenant_should_notBulkDeleteIt()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);

      // Act
      int bulkDelete = bulkDeleteScenarios(headerRoute(memberTenant), scenarioId);

      // Assert
      assertThat(bulkDelete).isEqualTo(404);
      assertThat(scenarioExists(scenarioId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario and no selector, a member of the default tenant bulk"
            + " deletes it")
    void given_defaultTenantScenarioAndNoSelector_should_bulkDeleteIt() throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);

      // Act
      int bulkDelete = bulkDeleteScenarios(noSelectorRoute(), scenarioId);

      // Assert
      assertThat(bulkDelete).isEqualTo(200);
      assertThat(scenarioExists(scenarioId)).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant simulation and a selector naming another tenant, the bulk delete"
            + " answers not found and keeps it")
    void given_defaultTenantSimulationAndSelectorNamingAnotherTenant_should_notBulkDeleteIt()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);

      // Act
      int bulkDelete = bulkDeleteSimulations(headerRoute(memberTenant), simulationId);

      // Assert
      assertThat(bulkDelete).isEqualTo(404);
      assertThat(simulationExists(simulationId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant simulation and no selector, a member of the default tenant bulk"
            + " deletes it")
    void given_defaultTenantSimulationAndNoSelector_should_bulkDeleteIt() throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);

      // Act
      int bulkDelete = bulkDeleteSimulations(noSelectorRoute(), simulationId);

      // Assert
      assertThat(bulkDelete).isEqualTo(200);
      assertThat(simulationExists(simulationId)).isFalse();
    }
  }

  @Nested
  @DisplayName("Health checks and player lists of a simulation or scenario")
  class HealthChecksAndPlayerLists {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant simulation on the route of another tenant, its health checks,"
            + " players, logs and comchecks answer not found")
    void given_defaultTenantSimulationOnRouteOfAnotherTenant_should_refuseItsSubResources()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      Route route = tenantRoute(memberTenant);

      // Act
      int healthChecks = getSimulationHealthChecks(route, simulationId);
      int players = getSimulationPlayers(route, simulationId);
      int logs = getSimulationLogs(route, simulationId);
      int comchecks = getSimulationComchecks(route, simulationId);

      // Assert
      assertThat(new int[] {healthChecks, players, logs, comchecks})
          .containsExactly(404, 404, 404, 404);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant scenario on the route of another tenant, its health checks and"
            + " players answer not found")
    void given_defaultTenantScenarioOnRouteOfAnotherTenant_should_refuseItsSubResources()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);

      // Act
      int healthChecks = getScenarioHealthChecks(tenantRoute(memberTenant), scenarioId);
      int players = getScenarioPlayers(tenantRoute(memberTenant), scenarioId);

      // Assert
      assertThat(new int[] {healthChecks, players}).containsExactly(404, 404);
    }

    @Test
    @DisplayName("given a simulation of a member tenant, its player list holds its players")
    void given_simulationOfMemberTenant_should_listItsPlayers() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      String playerId = seedPlayerOfTeam(seedTeamOfSimulation(memberTenant, simulationId));

      // Act
      List<String> onTenantRoute = simulationPlayerIds(tenantRoute(memberTenant), simulationId);
      List<String> onHeaderRoute = simulationPlayerIds(headerRoute(memberTenant), simulationId);

      // Assert
      assertThat(onTenantRoute).containsExactly(playerId);
      assertThat(onHeaderRoute).containsExactly(playerId);
    }

    @Test
    @DisplayName("given a scenario of a member tenant, its player list holds its players")
    void given_scenarioOfMemberTenant_should_listItsPlayers() throws Exception {
      // Arrange
      String scenarioId = seedScenario(memberTenant);
      String playerId = seedPlayerOfTeam(seedTeamOfScenario(memberTenant, scenarioId));

      // Act
      List<String> onTenantRoute = scenarioPlayerIds(tenantRoute(memberTenant), scenarioId);
      List<String> onHeaderRoute = scenarioPlayerIds(headerRoute(memberTenant), scenarioId);

      // Assert
      assertThat(onTenantRoute).containsExactly(playerId);
      assertThat(onHeaderRoute).containsExactly(playerId);
    }

    @Test
    @DisplayName(
        "given a simulation of a member tenant, its health checks are answered on both routes")
    void given_simulationOfMemberTenant_should_answerItsHealthChecks() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);

      // Act
      int onTenantRoute = getSimulationHealthChecks(tenantRoute(memberTenant), simulationId);
      int onHeaderRoute = getSimulationHealthChecks(headerRoute(memberTenant), simulationId);

      // Assert
      assertThat(new int[] {onTenantRoute, onHeaderRoute}).containsExactly(200, 200);
    }

    @Test
    @DisplayName(
        "given a scenario of a member tenant, its health checks are answered on its tenant route,"
            + " and not on the selector until the table is isolated")
    void given_scenarioOfMemberTenant_should_answerItsHealthChecksOnItsTenantRoute()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(memberTenant);

      // Act
      int onTenantRoute = getScenarioHealthChecks(tenantRoute(memberTenant), scenarioId);
      int onHeaderRoute = getScenarioHealthChecks(headerRoute(memberTenant), scenarioId);

      // Assert
      // The scenario health checks resolve the scenario through the scope query, which the v1
      // entity filter also narrows to the ambient tenant, the default one on the non-prefixed
      // route. The simulation twin and both player lists resolve by primary key and answer on
      // both routes. Removing the filter at the activation turns the second status into 200.
      assertThat(new int[] {onTenantRoute, onHeaderRoute}).containsExactly(200, 404);
    }
  }
}
