package io.openaev.rest.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Simulation and scenario by-id reads with {@code exercises} and {@code scenarios} isolated on top
 * of the production list. The tenant route keeps working, a selector naming a tenant the caller is
 * not a member of is refused, and a row outside the selector is not found. The expectation drift
 * realignment, which opens no transaction of its own, resolves its row in the request scope too.
 *
 * <p>The simulation and scenario entities still carry the v1 tenant filter, keyed on the ambient
 * tenant, which the non-prefixed route leaves on the default one. So a member tenant's row named by
 * the selector is not found by id there yet; that row turns readable, updatable and deletable once
 * the filter comes off the two entities.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Simulation and scenario by-id reads in the request scope, both tables isolated")
class ByIdRequestScopeArmedTest extends ByIdRequestScopeTestBase {

  @DynamicPropertySource
  static void productionActiveTablesWithExercisesAndScenarios(DynamicPropertyRegistry registry) {
    Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("exercises");
    tables.add("scenarios");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
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
        "given a default tenant simulation and scenario and a selector naming another tenant,"
            + " neither is found by id")
    void given_defaultTenantRowsAndSelectorNamingAnotherTenant_should_notBeFound()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String scenarioId = seedScenario(DEFAULT_TENANT);
      Route route = headerRoute(memberTenant);

      // Act
      int simulationUpdate = updateSimulation(route, simulationId);
      int simulationDelete = deleteSimulation(route, simulationId);
      int scenarioUpdate = updateScenario(route, scenarioId);
      int scenarioDelete = deleteScenario(route, scenarioId);

      // Assert
      assertThat(new int[] {simulationUpdate, simulationDelete, scenarioUpdate, scenarioDelete})
          .containsExactly(404, 404, 404, 404);
      assertThat(simulationExists(simulationId)).isTrue();
      assertThat(scenarioExists(scenarioId)).isTrue();
    }

    @Test
    @DisplayName(
        "given a member tenant simulation and scenario named by the selector, neither is found by"
            + " id while the two entities keep the v1 filter")
    void given_memberTenantRowsNamedBySelector_should_notBeFoundUntilFilterRemoved()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      String scenarioId = seedScenario(memberTenant);
      Route route = headerRoute(memberTenant);

      // Act
      int simulationUpdate = updateSimulation(route, simulationId);
      int simulationDelete = deleteSimulation(route, simulationId);
      int scenarioUpdate = updateScenario(route, scenarioId);
      int scenarioDelete = deleteScenario(route, scenarioId);

      // Assert
      assertThat(new int[] {simulationUpdate, simulationDelete, scenarioUpdate, scenarioDelete})
          .containsExactly(404, 404, 404, 404);
      assertThat(simulationExists(simulationId)).isTrue();
      assertThat(scenarioExists(scenarioId)).isTrue();
    }

    @Test
    @DisplayName(
        "given a caller with no membership and no selector, a default tenant simulation and"
            + " scenario are not found by id")
    void given_callerWithNoMembershipAndNoSelector_should_notFindDefaultTenantRows()
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
      assertThat(new int[] {simulationUpdate, scenarioUpdate}).containsExactly(404, 404);
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
  @DisplayName("Expectation drift realignment")
  class ExpectationDriftRealignment {

    @Test
    @DisplayName(
        "given a simulation and a scenario of the path tenant, a dismissed drift is realigned and"
            + " the dismissal cleared")
    void given_rowsOfThePathTenant_should_realignAndClearTheDismissal() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      String scenarioId = seedScenario(memberTenant);
      Route route = tenantRoute(memberTenant);
      int simulationDismiss = dismissSimulationDrift(route, simulationId);
      int scenarioDismiss = dismissScenarioDrift(route, scenarioId);

      // Act
      int simulationRealign = realignSimulationDrift(route, simulationId);
      int scenarioRealign = realignScenarioDrift(route, scenarioId);

      // Assert
      assertThat(new int[] {simulationDismiss, scenarioDismiss, simulationRealign, scenarioRealign})
          .containsExactly(200, 200, 200, 200);
      assertThat(simulationDriftDismissed(simulationId)).isFalse();
      assertThat(scenarioDriftDismissed(scenarioId)).isFalse();
    }

    @Test
    @DisplayName(
        "given a simulation and a scenario of the tenant named by the selector, a dismissed drift"
            + " is realigned and the dismissal cleared")
    void given_rowsOfTheSelectedTenant_should_realignAndClearTheDismissal() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      String scenarioId = seedScenario(memberTenant);
      Route route = headerRoute(memberTenant);
      int simulationDismiss = dismissSimulationDrift(route, simulationId);
      int scenarioDismiss = dismissScenarioDrift(route, scenarioId);

      // Act
      int simulationRealign = realignSimulationDrift(route, simulationId);
      int scenarioRealign = realignScenarioDrift(route, scenarioId);

      // Assert
      assertThat(new int[] {simulationDismiss, scenarioDismiss, simulationRealign, scenarioRealign})
          .containsExactly(200, 200, 200, 200);
      assertThat(simulationDriftDismissed(simulationId)).isFalse();
      assertThat(scenarioDriftDismissed(scenarioId)).isFalse();
    }

    @Test
    @DisplayName(
        "given a simulation and a scenario outside the path tenant, the realignment answers not"
            + " found")
    void given_rowsOutsideThePathTenant_should_answerNotFound() throws Exception {
      // Arrange
      String simulationId = seedSimulation(otherTenant);
      String scenarioId = seedScenario(otherTenant);
      Route route = tenantRoute(memberTenant);

      // Act
      int simulationRealign = realignSimulationDrift(route, simulationId);
      int scenarioRealign = realignScenarioDrift(route, scenarioId);

      // Assert
      assertThat(new int[] {simulationRealign, scenarioRealign}).containsExactly(404, 404);
    }
  }
}
