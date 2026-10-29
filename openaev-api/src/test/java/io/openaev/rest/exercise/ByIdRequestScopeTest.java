package io.openaev.rest.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
}
