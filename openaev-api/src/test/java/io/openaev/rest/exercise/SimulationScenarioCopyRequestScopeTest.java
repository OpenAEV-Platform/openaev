package io.openaev.rest.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A copy of a simulation or a scenario carries every authored inject of its source, whichever route
 * names the tenant. The source is resolved in the request's tenant scope, and its injects are read
 * while the copy is built: the copy is complete only when that read follows the source's tenant,
 * not the tenant the request thread falls back to.
 */
@DisplayName("Simulation and scenario copy, in the tenant of the source")
class SimulationScenarioCopyRequestScopeTest extends ByIdRequestScopeEnterpriseEditionTestBase {

  @Nested
  @DisplayName("Non-prefixed route with an X-Tenant-Ids selector")
  class HeaderRoute {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given a simulation of the selected tenant holding an inject, its duplicate holds a copy"
            + " of the inject in that tenant")
    void given_simulationOfSelectedTenantWithInject_should_duplicateItsInjects() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      seedInjectOfSimulation(memberTenant, simulationId);

      // Act
      String duplicateId = duplicatedSimulationId(headerRoute(memberTenant), simulationId);

      // Assert
      assertThat(duplicateId).as("a duplicate was created").isNotNull();
      assertThat(injectTenants(duplicateId)).containsExactly(memberTenant);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given a scenario of the selected tenant holding an inject, its duplicate holds a copy of"
            + " the inject in that tenant")
    void given_scenarioOfSelectedTenantWithInject_should_duplicateItsInjects() throws Exception {
      // Arrange
      String scenarioId = seedScenario(memberTenant);
      seedInjectOfScenario(memberTenant, scenarioId);

      // Act
      String duplicateId = duplicatedScenarioId(headerRoute(memberTenant), scenarioId);

      // Assert
      assertThat(duplicateId).as("a duplicate was created").isNotNull();
      assertThat(scenarioInjectTenants(duplicateId)).containsExactly(memberTenant);
    }
  }

  @Nested
  @DisplayName("Tenant route")
  class TenantRoute {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given a simulation of the path tenant holding an inject, its duplicate holds a copy of"
            + " the inject in that tenant")
    void given_simulationOfPathTenantWithInject_should_duplicateItsInjects() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      seedInjectOfSimulation(memberTenant, simulationId);

      // Act
      String duplicateId = duplicatedSimulationId(tenantRoute(memberTenant), simulationId);

      // Assert
      assertThat(duplicateId).as("a duplicate was created").isNotNull();
      assertThat(injectTenants(duplicateId)).containsExactly(memberTenant);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given a scenario of the path tenant holding an inject, its duplicate holds a copy of the"
            + " inject in that tenant")
    void given_scenarioOfPathTenantWithInject_should_duplicateItsInjects() throws Exception {
      // Arrange
      String scenarioId = seedScenario(memberTenant);
      seedInjectOfScenario(memberTenant, scenarioId);

      // Act
      String duplicateId = duplicatedScenarioId(tenantRoute(memberTenant), scenarioId);

      // Assert
      assertThat(duplicateId).as("a duplicate was created").isNotNull();
      assertThat(scenarioInjectTenants(duplicateId)).containsExactly(memberTenant);
    }
  }
}
