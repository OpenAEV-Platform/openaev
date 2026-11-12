package io.openaev.rest.injector_contract;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.TENANT_INJECTOR_CONTRACT_URL;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the rule in docs/docs/development/tenant-isolation.md: a grant counts in the tenant of the
 * group that holds it, and on the regular (non-prefixed) route the ambient tenant is the default
 * one and is always part of the check, whatever the {@code X-Tenant-Ids} header names or whether it
 * is sent at all. A grant held only through a group of another tenant B therefore never passes on
 * the regular route, with or without a header naming B, while it keeps access on the {@code
 * /api/tenants/B/...} route. A grant held through a platform group counts in every tenant and is
 * unaffected.
 *
 * <p>The caller is a non-admin, a member of the default tenant and of tenant B, and holds no
 * capability in either: every access below comes from the grant alone.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("On the regular route, a tenant-group grant counts only in the ambient tenant")
class GrantTenantRegularPathTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String TENANT_AUTONOMOUS_URI = TENANT_PREFIX + "/autonomous-runs";
  private static final String CONTRACT_URI = INJECTOR_CONTRACT_URL + "/{contractId}";
  private static final String TENANT_CONTRACT_URI = TENANT_INJECTOR_CONTRACT_URL + "/{contractId}";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private TestUserHolder testUserHolder;

  // The autonomous run endpoint is EE-gated; the mock's license checks default to active.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantB;

  @BeforeEach
  void setup() throws Exception {
    tenantGroupComposer.reset();
    platformGroupComposer.reset();
    grantComposer.reset();
    scenarioComposer.reset();
    injectorContractComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("grant-regular-path-b").getId();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("A custom injector contract of B, granted through a group of B")
  class ContractGrantedThroughTenantGroup {

    @Test
    @DisplayName("Prefixed route of B: the grant counts")
    void given_pathNamingGrantTenant_should_readContract() throws Exception {
      // Arrange
      String contractId = grantedContract();

      // Act / Assert
      mvc.perform(prefixed(TENANT_CONTRACT_URI, contractId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Header naming B: the grant does not count, the ambient tenant refuses")
    void given_headerNamingGrantTenant_should_refuseWithAmbientBody() throws Exception {
      // Arrange
      String contractId = grantedContract();

      // Act
      MockHttpServletResponse response = perform(nonPrefixedWithHeader(CONTRACT_URI, contractId));

      // Assert
      assertAmbientRefusal(response);
    }

    @Test
    @DisplayName("No selector: the grant does not count, the ambient tenant refuses")
    void given_noSelector_should_refuseWithAmbientBody() throws Exception {
      // Arrange
      String contractId = grantedContract();

      // Act
      MockHttpServletResponse response = perform(nonPrefixedNoHeader(CONTRACT_URI, contractId));

      // Assert
      assertAmbientRefusal(response);
    }
  }

  @Nested
  @DisplayName("An autonomous run of B bound to a scenario granted through a group of B")
  class AutonomousRunGrantedThroughTenantGroup {

    @Test
    @DisplayName("Prefixed route of B: the grant counts")
    void given_pathNamingGrantTenant_should_readRun() throws Exception {
      // Arrange
      String runId = runBoundToGrantedScenario();

      // Act / Assert
      mvc.perform(prefixed(TENANT_AUTONOMOUS_URI + "/{runId}", runId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Header naming B: the grant does not count, the ambient tenant refuses")
    void given_headerNamingGrantTenant_should_refuseWithAmbientBody() throws Exception {
      // Arrange
      String runId = runBoundToGrantedScenario();

      // Act
      MockHttpServletResponse response =
          perform(nonPrefixedWithHeader(AUTONOMOUS_URI + "/{runId}", runId));

      // Assert
      assertAmbientRefusal(response);
    }

    @Test
    @DisplayName("No selector: the grant does not count, the ambient tenant refuses")
    void given_noSelector_should_refuseWithAmbientBody() throws Exception {
      // Arrange
      String runId = runBoundToGrantedScenario();

      // Act
      MockHttpServletResponse response =
          perform(nonPrefixedNoHeader(AUTONOMOUS_URI + "/{runId}", runId));

      // Assert
      assertAmbientRefusal(response);
    }

    private String runBoundToGrantedScenario() {
      return runBoundTo(grantedScenario());
    }
  }

  /**
   * Contrast case: the same grant, held through a platform group instead of a tenant group of B.
   * Injector contracts cannot show this contrast on the regular route: {@code injectors_contracts}
   * is a v1 table, so its read is hidden by the Hibernate {@code tenantFilter} on the ambient
   * (default) tenant regardless of the grant, which is unrelated to this rule. Autonomous runs are
   * v2-active, so their read is governed by the request's {@code TxCtx} scope, which names B on
   * both the header and the no-selector case, and by the grant check alone.
   */
  @Nested
  @DisplayName("An autonomous run of B bound to a scenario granted through a platform group")
  class AutonomousRunGrantedThroughPlatformGroup {

    @Test
    @DisplayName("Header naming B: the platform grant counts in every tenant")
    void given_headerNamingB_should_readRun() throws Exception {
      // Arrange
      String runId = runBoundToGrantedScenarioThroughPlatformGroup();

      // Act / Assert
      mvc.perform(nonPrefixedWithHeader(AUTONOMOUS_URI + "/{runId}", runId))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("No selector: the platform grant counts in every tenant")
    void given_noSelector_should_readRun() throws Exception {
      // Arrange
      String runId = runBoundToGrantedScenarioThroughPlatformGroup();

      // Act / Assert
      mvc.perform(nonPrefixedNoHeader(AUTONOMOUS_URI + "/{runId}", runId))
          .andExpect(status().isOk());
    }

    private String runBoundToGrantedScenarioThroughPlatformGroup() {
      return runBoundTo(grantedScenarioThroughPlatformGroup());
    }
  }

  // -- Helpers --

  private String runBoundTo(String scenarioId) {
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

  private String grantedContract() {
    String contractId = createContractInTenantB("grant regular-path contract");
    grantThroughTenantBGroup(
        GrantFixture.getGrantForThreatArsenal(contractId, Grant.GRANT_TYPE.OBSERVER));
    return contractId;
  }

  private String createContractInTenantB(String label) {
    TenantContext.setCurrentTenant(tenantB);
    try {
      InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
      contract.setLabels(Map.of("en", label));
      String contractId =
          injectorContractComposer.forInjectorContract(contract).persist().get().getId();
      entityManager.flush();
      return contractId;
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private String grantedScenario() {
    Scenario scenario = createScenarioInTenantB();
    grantThroughTenantBGroup(GrantFixture.getGrantForScenario(scenario, Grant.GRANT_TYPE.OBSERVER));
    return scenario.getId();
  }

  private String grantedScenarioThroughPlatformGroup() {
    Scenario scenario = createScenarioInTenantB();
    Group group = PlatformGroupFixture.getPlatformGroup("Grant holders platform");
    group.setUsers(new ArrayList<>(List.of(testUserHolder.get())));
    platformGroupComposer.forPlatformGroup(group).persist();
    Grant grant = GrantFixture.getGrantForScenario(scenario, Grant.GRANT_TYPE.OBSERVER);
    grant.setGroup(group);
    grantComposer.forGrant(grant).persist();
    reloadCurrentUser();
    return scenario.getId();
  }

  private Scenario createScenarioInTenantB() {
    return inTenantB(
        () -> scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get());
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
    reloadCurrentUser();
  }

  private void reloadCurrentUser() {
    entityManager.flush();
    entityManager.clear();
    testUserHolder.refreshSecurityContext();
  }

  private MockHttpServletRequestBuilder nonPrefixedWithHeader(String uri, String id) {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return get(uri, id).header(TENANT_IDS_HEADER, tenantB);
  }

  private MockHttpServletRequestBuilder nonPrefixedNoHeader(String uri, String id) {
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return get(uri, id);
  }

  private MockHttpServletRequestBuilder prefixed(String uri, String id) {
    TenantContext.clearCurrentTenant();
    return get(uri, tenantB, id);
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andReturn().getResponse();
  }

  /**
   * The ambient (default-tenant) refusal from {@code AccessControlAspect.methodRBACVerification}
   * (openaev-api/src/main/java/io/openaev/aop/ AccessControlAspect.java:149-153): a plain 403 with
   * no TENANT_SCOPE_FORBIDDEN body. The check in the ambient tenant fails before any other scope
   * tenant is ever evaluated (grant-service.java :56-57: no grant counts for the default tenant,
   * since the group holding it is tenant B's, not the default tenant's and not a platform group),
   * so {@code TenantScopeForbiddenException} (thrown only past that first check, at
   * AccessControlAspect.java :160) is never reached.
   */
  private void assertAmbientRefusal(MockHttpServletResponse response) throws Exception {
    assertAll(
        () -> assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus(), "refused"),
        () -> assertNotNull(response.getErrorMessage(), "the ambient refusal reason"),
        () ->
            assertTrue(
                response.getErrorMessage().startsWith("Access denied for user"),
                "the ambient refusal reason"),
        () ->
            assertFalse(
                response.getContentAsString().contains("TENANT_SCOPE_FORBIDDEN"),
                "no scope rule in the body"));
  }
}
