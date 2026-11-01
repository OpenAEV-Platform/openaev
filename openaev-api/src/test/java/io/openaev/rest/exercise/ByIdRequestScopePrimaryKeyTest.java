package io.openaev.rest.exercise;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.inject.form.DirectInjectInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A simulation or scenario that an endpoint loads by primary key is resolved inside the request's
 * tenant scope: the path tenant on the tenant route, the {@code X-Tenant-Ids} selector or the
 * caller's memberships on the non-prefixed route. Covers the direct inject execution, the
 * expectations, expectation drift and player challenges endpoints, and the scenario delete with an
 * autonomous run. Runs with the production list of isolated tables.
 *
 * <p>The direct inject execution also writes: the rows it creates belong to the simulation's
 * tenant, which the last group asserts on the inject row itself on all three routes, so counting
 * the rows is not taken for proof of where they landed.
 */
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@DisplayName("Simulation and scenario primary key loads in the request scope, production tables")
class ByIdRequestScopePrimaryKeyTest extends ByIdRequestScopeTestBase {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  // -- REQUESTS --

  private int perform(Route route, MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    return mvc.perform(route.with(request)).andReturn().getResponse().getStatus();
  }

  private int simulationExpectations(Route route, String id) throws Exception {
    return perform(route, get(route.simulation(id) + "/expectations"));
  }

  private int simulationDrift(Route route, String id) throws Exception {
    return perform(route, get(route.simulation(id) + "/expectations-drift"));
  }

  private int simulationPlayerChallenges(Route route, String id) throws Exception {
    return perform(route, get(route.simulationPlayerChallenges(id)));
  }

  private int scenarioDrift(Route route, String id) throws Exception {
    return perform(route, get(route.scenario(id) + "/expectations-drift"));
  }

  private int executeDirectInject(Route route, String simulationId, String contractId)
      throws Exception {
    DirectInjectInput input = new DirectInjectInput();
    input.setTitle("by-id direct inject");
    input.setInjectorContract(contractId);
    input.setContent(MAPPER.createObjectNode());
    input.setUserIds(List.of());
    MockMultipartFile inputPart =
        new MockMultipartFile(
            "input", "", MediaType.APPLICATION_JSON_VALUE, asJsonString(input).getBytes());
    MockMultipartFile filePart =
        new MockMultipartFile("file", "by-id.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);
    var request =
        multipart(route.simulation(simulationId) + "/inject")
            .file(inputPart)
            .file(filePart)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf());
    if (route.headerTenant() != null) {
      request.header(TENANT_IDS_HEADER, route.headerTenant());
    }
    TenantContext.clearCurrentTenant();
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }

  @Nested
  @DisplayName("Tenant route, rows of the default tenant on the route of another tenant")
  class TenantRoute {

    @Test
    @DisplayName("given a simulation outside the path tenant, its endpoints answer not found")
    void given_simulationOutsideThePathTenant_should_answerNotFoundOnEveryEndpoint()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String contractId = seedInjectorContract(memberTenant);
      Route route = tenantRoute(memberTenant);

      // Act
      int expectations = simulationExpectations(route, simulationId);
      int drift = simulationDrift(route, simulationId);
      int dismiss = dismissSimulationDrift(route, simulationId);
      int realign = realignSimulationDrift(route, simulationId);
      int challenges = simulationPlayerChallenges(route, simulationId);
      int execute = executeDirectInject(route, simulationId, contractId);

      // Assert
      assertThat(new int[] {expectations, drift, dismiss, realign, challenges, execute})
          .containsExactly(404, 404, 404, 404, 404, 404);
      assertThat(simulationDriftDismissed(simulationId)).isFalse();
      assertThat(injectCount(simulationId)).isZero();
    }

    @Test
    @DisplayName("given a scenario outside the path tenant, its drift endpoints answer not found")
    void given_scenarioOutsideThePathTenant_should_answerNotFoundOnEveryEndpoint()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      Route route = tenantRoute(memberTenant);

      // Act
      int drift = scenarioDrift(route, scenarioId);
      int dismiss = dismissScenarioDrift(route, scenarioId);
      int realign = realignScenarioDrift(route, scenarioId);

      // Assert
      assertThat(new int[] {drift, dismiss, realign}).containsExactly(404, 404, 404);
      assertThat(scenarioDriftDismissed(scenarioId)).isFalse();
    }

    @Test
    @DisplayName("given a simulation of the path tenant, its endpoints are reached")
    void given_simulationOfThePathTenant_should_reachEveryEndpoint() throws Exception {
      // Arrange
      String simulationId = seedSimulation(memberTenant);
      String contractId = seedInjectorContract(memberTenant);
      Route route = tenantRoute(memberTenant);

      // Act
      int expectations = simulationExpectations(route, simulationId);
      int drift = simulationDrift(route, simulationId);
      int dismiss = dismissSimulationDrift(route, simulationId);
      int realign = realignSimulationDrift(route, simulationId);
      int challenges = simulationPlayerChallenges(route, simulationId);
      int execute = executeDirectInject(route, simulationId, contractId);

      // Assert
      assertThat(new int[] {expectations, drift, dismiss, realign, challenges, execute})
          .containsExactly(200, 200, 200, 200, 200, 200);
      assertThat(simulationDriftDismissed(simulationId)).isFalse();
      assertThat(injectCount(simulationId)).isEqualTo(1);
      assertThat(injectTenants(simulationId)).containsExactly(memberTenant);
    }
  }

  @Nested
  @DisplayName("Non-prefixed route")
  class NonPrefixedRoute {

    @Test
    @DisplayName(
        "given a default tenant simulation and a selector naming another tenant, its endpoints"
            + " answer not found and change nothing")
    void given_defaultTenantSimulationAndOtherSelector_should_answerNotFoundOnEveryEndpoint()
        throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String contractId = seedInjectorContract(DEFAULT_TENANT);
      Route route = headerRoute(memberTenant);

      // Act
      int expectations = simulationExpectations(route, simulationId);
      int drift = simulationDrift(route, simulationId);
      int dismiss = dismissSimulationDrift(route, simulationId);
      int realign = realignSimulationDrift(route, simulationId);
      int challenges = simulationPlayerChallenges(route, simulationId);
      int execute = executeDirectInject(route, simulationId, contractId);

      // Assert
      assertThat(new int[] {expectations, drift, dismiss, realign, challenges, execute})
          .containsExactly(404, 404, 404, 404, 404, 404);
      assertThat(simulationDriftDismissed(simulationId)).isFalse();
      assertThat(injectCount(simulationId)).isZero();
    }

    @Test
    @DisplayName(
        "given a default tenant scenario and a selector naming another tenant, its drift"
            + " endpoints answer not found and change nothing")
    void given_defaultTenantScenarioAndOtherSelector_should_answerNotFoundOnEveryEndpoint()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      Route route = headerRoute(memberTenant);

      // Act
      int drift = scenarioDrift(route, scenarioId);
      int dismiss = dismissScenarioDrift(route, scenarioId);
      int realign = realignScenarioDrift(route, scenarioId);

      // Assert
      assertThat(new int[] {drift, dismiss, realign}).containsExactly(404, 404, 404);
      assertThat(scenarioDriftDismissed(scenarioId)).isFalse();
    }

    @Test
    @DisplayName(
        "given a scenario with an active run outside the selected tenant, its delete answers not"
            + " found")
    void given_scenarioWithActiveRunOutsideTheSelector_should_answerNotFoundOnDelete()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      seedActiveRunOfScenario(DEFAULT_TENANT, scenarioId);
      Route route = headerRoute(memberTenant);

      // Act
      int delete = perform(route, delete(route.scenario(scenarioId)));

      // Assert
      assertThat(delete).isEqualTo(404);
      assertThat(scenarioExists(scenarioId)).isTrue();
      assertThat(autonomousRunCount(scenarioId)).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "given a default tenant simulation and no selector, a member of the default tenant"
            + " reaches its endpoints")
    void given_defaultTenantSimulationAndNoSelector_should_reachEveryEndpoint() throws Exception {
      // Arrange
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String contractId = seedInjectorContract(DEFAULT_TENANT);
      Route route = noSelectorRoute();

      // Act
      int expectations = simulationExpectations(route, simulationId);
      int drift = simulationDrift(route, simulationId);
      int dismiss = dismissSimulationDrift(route, simulationId);
      int realign = realignSimulationDrift(route, simulationId);
      int challenges = simulationPlayerChallenges(route, simulationId);
      int execute = executeDirectInject(route, simulationId, contractId);

      // Assert
      assertThat(new int[] {expectations, drift, dismiss, realign, challenges, execute})
          .containsExactly(200, 200, 200, 200, 200, 200);
      assertThat(simulationDriftDismissed(simulationId)).isFalse();
      assertThat(injectCount(simulationId)).isEqualTo(1);
      assertThat(injectTenants(simulationId)).containsExactly(DEFAULT_TENANT);
    }

    @Test
    @DisplayName(
        "given a default tenant scenario and no selector, a member of the default tenant reaches"
            + " its drift endpoints")
    void given_defaultTenantScenarioAndNoSelector_should_reachDriftEndpoints() throws Exception {
      // Arrange
      String scenarioId = seedScenario(DEFAULT_TENANT);
      Route route = noSelectorRoute();

      // Act
      int drift = scenarioDrift(route, scenarioId);
      int dismiss = dismissScenarioDrift(route, scenarioId);
      int realign = realignScenarioDrift(route, scenarioId);

      // Assert
      assertThat(new int[] {drift, dismiss, realign}).containsExactly(200, 200, 200);
      assertThat(scenarioDriftDismissed(scenarioId)).isFalse();
    }
  }

  @Nested
  @DisplayName("The direct inject execution writes in the simulation's tenant")
  class DirectExecutionWriteTenant {

    @Test
    @DisplayName("given a simulation of another tenant and no selector, the inject carries it")
    void given_simulationOfAnotherTenantAndNoSelector_should_writeTheInjectInItsTenant()
        throws Exception {
      // Arrange: the caller is a member of the default tenant and of the member tenant, so a
      // request naming no tenant resolves both; off the tenant route the ambient tenant is the
      // default one, which is not the simulation's.
      String simulationId = seedSimulation(memberTenant);
      String contractId = seedInjectorContract(memberTenant);

      // Act
      int execute = executeDirectInject(noSelectorRoute(), simulationId, contractId);

      // Assert
      assertThat(execute).isEqualTo(200);
      assertThat(injectTenants(simulationId)).containsExactly(memberTenant);
    }

    @Test
    @DisplayName("given a simulation of the selected tenant, the inject carries it")
    void given_simulationOfTheSelectedTenant_should_writeTheInjectInItsTenant() throws Exception {
      // Arrange: the selector pins the simulation's tenant while the ambient tenant off the
      // tenant route stays the default one.
      String simulationId = seedSimulation(memberTenant);
      String contractId = seedInjectorContract(memberTenant);

      // Act
      int execute = executeDirectInject(headerRoute(memberTenant), simulationId, contractId);

      // Assert
      assertThat(execute).isEqualTo(200);
      assertThat(injectTenants(simulationId)).containsExactly(memberTenant);
    }

    @Test
    @DisplayName("given a simulation of the path tenant, the inject carries it")
    void given_simulationOfThePathTenant_should_writeTheInjectInItsTenant() throws Exception {
      // Arrange: on the tenant route the ambient tenant already is the simulation's.
      String simulationId = seedSimulation(memberTenant);
      String contractId = seedInjectorContract(memberTenant);

      // Act
      int execute = executeDirectInject(tenantRoute(memberTenant), simulationId, contractId);

      // Assert
      assertThat(execute).isEqualTo(200);
      assertThat(injectTenants(simulationId)).containsExactly(memberTenant);
    }

    @Test
    @DisplayName("given a threat arsenal item of another tenant, nothing is written")
    void given_contractOutsideTheSimulationTenant_should_answerNotFoundAndWriteNothing()
        throws Exception {
      // Arrange: the simulation belongs to the member tenant and the item to the default one,
      // which is also the ambient tenant off the tenant route; the request scope holds both.
      String simulationId = seedSimulation(memberTenant);
      String contractId = seedInjectorContract(DEFAULT_TENANT);

      // Act
      int execute = executeDirectInject(noSelectorRoute(), simulationId, contractId);

      // Assert: the item is looked up in the simulation's tenant, where it does not exist
      assertThat(injectTenants(simulationId))
          .as("no inject row, in the simulation's tenant or in any other")
          .isEmpty();
      assertThat(execute).isEqualTo(404);
    }

    @Test
    @DisplayName("given a simulation of the default tenant and no selector, the inject carries it")
    void given_defaultTenantSimulationAndNoSelector_should_writeTheInjectInTheDefaultTenant()
        throws Exception {
      // Arrange: the member contrast of the three cases above, where the simulation's tenant and
      // the ambient one are the same.
      String simulationId = seedSimulation(DEFAULT_TENANT);
      String contractId = seedInjectorContract(DEFAULT_TENANT);

      // Act
      int execute = executeDirectInject(noSelectorRoute(), simulationId, contractId);

      // Assert
      assertThat(execute).isEqualTo(200);
      assertThat(injectTenants(simulationId)).containsExactly(DEFAULT_TENANT);
    }
  }
}
