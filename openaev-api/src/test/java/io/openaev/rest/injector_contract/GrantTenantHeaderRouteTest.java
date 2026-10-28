package io.openaev.rest.injector_contract;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.TENANT_INJECTOR_CONTRACT_URL;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A grant held through a group of tenant B, read on the non-prefixed route with a header naming B.
 * The caller is a member of the default tenant and of B and holds no capability in either, so every
 * access comes from the grant alone. The prefixed route of B is the control.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("A grant of a tenant B group, read on the non-prefixed route with a header naming B")
class GrantTenantHeaderRouteTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String TENANT_AUTONOMOUS_URI = TENANT_PREFIX + "/autonomous-runs";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private TestUserHolder testUserHolder;

  // The autonomous run endpoints are EE-gated; the mock's license checks default to active.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantB;

  @BeforeEach
  void setup() throws Exception {
    tenantGroupComposer.reset();
    grantComposer.reset();
    scenarioComposer.reset();
    injectorContractComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("grant-header-b").getId();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Autonomous run of B bound to a scenario granted in B")
  class AutonomousRun {

    @Test
    @DisplayName("On the non-prefixed route with a header naming B, the run is readable")
    void given_headerNamingGrantTenant_should_readRun() throws Exception {
      // Arrange
      String runId = runBoundToGrantedScenario();

      // Act & Assert
      mvc.perform(nonPrefixed(AUTONOMOUS_URI + "/{runId}", runId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("On the prefixed route of B, the run is readable")
    void given_pathNamingGrantTenant_should_readRun() throws Exception {
      // Arrange
      String runId = runBoundToGrantedScenario();

      // Act & Assert
      mvc.perform(prefixed(TENANT_AUTONOMOUS_URI + "/{runId}", runId)).andExpect(status().isOk());
    }

    private String runBoundToGrantedScenario() {
      String scenarioId = grantedScenario();
      String runId = UUID.randomUUID().toString();
      // Native seeding: an explicit tenant_id lands the row without a request scope, and COMPLETED
      // keeps the read-path reconcile a no-op.
      entityManager
          .createNativeQuery(
              "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                  + " autonomous_run_objective, autonomous_run_status, autonomous_run_scenario_id)"
                  + " VALUES (:id, :tenant, 'Own the domain', 'COMPLETED', :scenario)")
          .setParameter("id", runId)
          .setParameter("tenant", tenantB)
          .setParameter("scenario", scenarioId)
          .executeUpdate();
      return runId;
    }
  }

  @Nested
  @DisplayName("Scenario of B granted in B")
  class GrantedScenario {

    @Test
    @DisplayName("On the non-prefixed route with a header naming B, the scenario is readable")
    void given_headerNamingGrantTenant_should_readScenario() throws Exception {
      // Arrange
      String scenarioId = grantedScenario();

      // Act & Assert
      mvc.perform(nonPrefixed(SCENARIO_URI + "/{scenarioId}", scenarioId))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("On the prefixed route of B, the scenario is readable")
    void given_pathNamingGrantTenant_should_readScenario() throws Exception {
      // Arrange
      String scenarioId = grantedScenario();

      // Act & Assert
      mvc.perform(prefixed(TENANT_SCENARIO_URI + "/{scenarioId}", scenarioId))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Custom injector contract of B granted in B")
  class GrantedContract {

    @Test
    @DisplayName("On the non-prefixed route with a header naming B, the contract is readable")
    void given_headerNamingGrantTenant_should_readContract() throws Exception {
      // Arrange
      String contractId = grantedContract();

      // Act & Assert
      mvc.perform(nonPrefixed(INJECTOR_CONTRACT_URL + "/{contractId}", contractId))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("On the prefixed route of B, the contract is readable")
    void given_pathNamingGrantTenant_should_readContract() throws Exception {
      // Arrange
      String contractId = grantedContract();

      // Act & Assert
      mvc.perform(prefixed(TENANT_INJECTOR_CONTRACT_URL + "/{contractId}", contractId))
          .andExpect(status().isOk());
    }
  }

  // -- Helpers --

  private String grantedScenario() {
    Scenario scenario =
        inTenantB(
            () -> scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get());
    grantThroughTenantBGroup(GrantFixture.getGrantForScenario(scenario, Grant.GRANT_TYPE.OBSERVER));
    return scenario.getId();
  }

  private String grantedContract() {
    TenantContext.setCurrentTenant(tenantB);
    String contractId;
    try {
      InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
      contract.setLabels(Map.of("en", "grant header contract"));
      contractId = injectorContractComposer.forInjectorContract(contract).persist().get().getId();
      entityManager.flush();
    } finally {
      TenantContext.clearCurrentTenant();
    }
    grantThroughTenantBGroup(
        GrantFixture.getGrantForThreatArsenal(contractId, Grant.GRANT_TYPE.OBSERVER));
    return contractId;
  }

  private <T> T inTenantB(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantB);
    try {
      return work.get();
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void grantThroughTenantBGroup(Grant grant) {
    TenantContext.setCurrentTenant(tenantB);
    try {
      Group group = TenantGroupFixture.getGroup("Grant holders B");
      group.setUsers(new ArrayList<>(List.of(testUserHolder.get())));
      tenantGroupComposer.forGroup(group).withGrant(grantComposer.forGrant(grant)).persist();
      entityManager.flush();
    } finally {
      TenantContext.clearCurrentTenant();
    }
    entityManager.clear();
    testUserHolder.refreshSecurityContext();
  }

  private MockHttpServletRequestBuilder nonPrefixed(String uri, String id) {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return get(uri, id).header(TENANT_IDS_HEADER, tenantB);
  }

  private MockHttpServletRequestBuilder prefixed(String uri, String id) {
    TenantContext.clearCurrentTenant();
    return get(uri, tenantB, id);
  }
}
