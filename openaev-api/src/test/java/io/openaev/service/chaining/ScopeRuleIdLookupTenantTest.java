package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
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
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asset ids stored in the scope rules of a workflow resolve in the tenant of the workflow.
 *
 * <p>The workflow and its scope rules live in tenant A. Its allowlist names one endpoint of tenant
 * A and one endpoint of tenant B: a rule stores the id its request names, and the rule carries the
 * tenant of its workflow. A request naming both tenants on the non-prefixed route holds both in its
 * scope, as the plan-scenario route does when it writes scope rules. The class arms the production
 * list.
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

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScopeService scopeService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowComposer workflowComposer;

  private String tenantA;
  private String tenantB;
  private String workflowId;
  private String endpointInA;
  private String endpointInB;

  @BeforeEach
  void seedWorkflowOfTenantAWithRulesNamingBothTenants() throws Exception {
    endpointComposer.reset();
    scenarioComposer.reset();
    workflowComposer.reset();
    // The caller belongs to both tenants, so the header can name both of them.
    tenantA = tenantHelper.createTenantWithCurrentUser("scope-rule-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("scope-rule-b").getId();

    endpointInA = saveEndpoint(tenantA, "scope-rule-own-endpoint");
    endpointInB = saveEndpoint(tenantB, "scope-rule-foreign-endpoint");

    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantA));
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setTenant(new Tenant(tenantA));
    workflow.setSafeModeEnabled(true);
    workflow
        .getWorkflowScopeRules()
        .addAll(List.of(assetRule(workflow, endpointInA), assetRule(workflow, endpointInB)));
    workflowComposer
        .forWorkflow(workflow)
        .withScenario(scenarioComposer.forScenario(scenario))
        .persist();
    workflowId = workflow.getId();

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
}
