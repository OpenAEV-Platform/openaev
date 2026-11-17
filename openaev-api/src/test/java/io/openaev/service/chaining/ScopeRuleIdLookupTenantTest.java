package io.openaev.service.chaining;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.AutonomousRunApi;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asset ids stored in the scope rules of a workflow resolve in the tenant of the workflow.
 *
 * <p>The workflow, its scope rules and the autonomous run live in tenant A. Its allowlist names one
 * endpoint of tenant A and one endpoint of tenant B: a rule stores the id its request names, and
 * the rule carries the tenant of its workflow. The caller belongs to both tenants, and the
 * non-prefixed autonomous run route takes {@code X-Tenant-Ids}, so the request scope holds both
 * tenants. The plan-scenario route reaches {@code getValidAssets} under the same scope through the
 * scope rules it writes. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Scope rules resolve the asset ids they store in the tenant of the workflow")
class ScopeRuleIdLookupTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String RUN_SCOPE_URI = AutonomousRunApi.AUTONOMOUS_URI + "/{runId}/scope";
  private static final String TENANT_RUN_SCOPE_URI =
      TENANT_PREFIX + AutonomousRunApi.AUTONOMOUS_URI.substring("/api".length()) + "/{runId}/scope";
  private static final String OWN_NAME = "scope-rule-own-endpoint";
  private static final String FOREIGN_NAME = "scope-rule-foreign-endpoint";

  // The run endpoints are EE-gated; the mock's license checks are set to "active" in the seed.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScopeService scopeService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowComposer workflowComposer;

  private String tenantA;
  private String tenantB;
  private String workflowId;
  private String scenarioId;
  private String runId;
  private String endpointInA;
  private String endpointInB;

  @BeforeEach
  void seedWorkflowOfTenantAWithRulesNamingBothTenants() throws Exception {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    endpointComposer.reset();
    scenarioComposer.reset();
    workflowComposer.reset();
    // The caller belongs to both tenants, so the header can name both of them.
    tenantA = tenantHelper.createTenantWithCurrentUser("scope-rule-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("scope-rule-b").getId();

    endpointInA = saveEndpoint(tenantA, OWN_NAME);
    endpointInB = saveEndpoint(tenantB, FOREIGN_NAME);

    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantA));
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setSafeModeEnabled(true);
    workflow
        .getWorkflowScopeRules()
        .addAll(List.of(assetRule(workflow, endpointInA), assetRule(workflow, endpointInB)));
    workflowComposer
        .forWorkflow(workflow)
        .withScenario(scenarioComposer.forScenario(scenario))
        .persist();
    workflowId = workflow.getId();
    scenarioId = scenario.getId();

    runId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_scenario_id) VALUES (?, ?, 'Own the domain',"
            + " 'COMPLETED', ?)",
        runId,
        tenantA,
        scenarioId);
    // The seed must leave the persistence context: a read would otherwise be served from it with
    // no SQL for the inspector to scope.
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Valid assets of a workflow")
  class ValidAssets {

    @Test
    @DisplayName(
        "given a scope holding both tenants when the rules name an endpoint of each tenant then"
            + " only the endpoint of the workflow tenant is in scope")
    void given_scopeHoldingBothTenants_should_resolveOnlyTheEndpointOfTheWorkflowTenant() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenants(List.of(tenantA, tenantB)));

      // Act
      List<Asset> assets = scopeService.getValidAssets(workflowId);

      // Assert
      assertThat(assets).extracting(Asset::getId).containsExactly(endpointInA);
    }

    @Test
    @DisplayName(
        "given a scope holding the workflow tenant alone when the rules name an endpoint of each"
            + " tenant then the endpoint of the workflow tenant is in scope")
    void given_scopeOfTheWorkflowTenant_should_resolveTheEndpointOfTheWorkflowTenant() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act
      List<Asset> assets = scopeService.getValidAssets(workflowId);

      // Assert
      assertThat(assets).extracting(Asset::getId).containsExactly(endpointInA);
    }
  }

  @Nested
  @DisplayName("Scope of an autonomous run")
  class RunScope {

    @Test
    @DisplayName(
        "given a header naming both tenants when the rules name an endpoint of each tenant then"
            + " only the endpoint of the run tenant carries its name")
    void given_headerNamingBothTenants_should_nameOnlyTheEndpointOfTheRunTenant() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      String response =
          mvc.perform(get(RUN_SCOPE_URI, runId).header(TENANT_HEADER, tenantA + "," + tenantB))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(nameOf(response, endpointInA)).isEqualTo(OWN_NAME);
      assertThat(nameOf(response, endpointInB)).isEqualTo(endpointInB);
    }

    @Test
    @DisplayName(
        "given the path of the run tenant when the rules name an endpoint of each tenant then only"
            + " the endpoint of the run tenant carries its name")
    void given_pathOfTheRunTenant_should_nameOnlyTheEndpointOfTheRunTenant() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_RUN_SCOPE_URI, tenantA, runId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(nameOf(response, endpointInA)).isEqualTo(OWN_NAME);
      assertThat(nameOf(response, endpointInB)).isEqualTo(endpointInB);
    }
  }

  // -- FIXTURES --

  private String saveEndpoint(String tenantId, String name) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setTenant(new Tenant(tenantId));
    endpointComposer.forEndpoint(endpoint).persist();
    return endpoint.getId();
  }

  private static WorkflowScopeRule assetRule(Workflow workflow, String endpointId) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
        .ruleSource(ScopeRuleSource.ASSET)
        .valueType(ScopeRuleValueType.ASSET_ID)
        .ruleValue(endpointId)
        .workflow(workflow)
        .build();
  }

  private static String nameOf(String response, String entryId) {
    List<String> names = JsonPath.read(response, "$.allowlist[?(@.id == '" + entryId + "')].name");
    assertThat(names).hasSize(1);
    return names.getFirst();
  }

  private void execute(String sql, String... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
              }
            });
  }
}
