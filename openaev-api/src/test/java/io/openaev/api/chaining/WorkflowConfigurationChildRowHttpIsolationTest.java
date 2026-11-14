package io.openaev.api.chaining;

import static io.openaev.api.chaining.WorkflowApi.TENANT_WORKFLOW_URI;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Session;
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
 * Isolation of {@code workflow_scope_rules} and {@code scope_variables} on their own rows, through
 * the real workflow configuration read of {@link WorkflowApi} on the tenant path.
 *
 * <p>Every scope rule and scope variable hangs off a workflow, and a workflow of another tenant is
 * already refused on {@code workflows}, so a case built on another tenant's workflow cannot tell
 * which table refused. Here the other tenant's rule and variable are attached to the caller's own
 * workflow: the parent is visible and only the child table's own scope stands between the caller
 * and the row. The seed is raw JDBC with an explicit tenant, which the statement inspector never
 * rewrites and which no endpoint would produce.
 *
 * <p>Each test stays on tenant A's path, so the per-request scope is the same for every request.
 */
@CoversTenantIsolation({"workflow_scope_rules", "scope_variables"})
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("workflow_scope_rules and scope_variables refuse another tenant's row on a workflow")
class WorkflowConfigurationChildRowHttpIsolationTest extends IntegrationTest {

  private static final String WORKFLOW_CONFIGURATION_URI =
      TENANT_WORKFLOW_URI + "/{workflowId}/configuration";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String workflowA;

  @BeforeEach
  void seedTwoTenantsAndOneWorkflowTemplateInA() throws Exception {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    tenantA = tenantHelper.createTenantWithCurrentUser("workflow-child-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("workflow-child-iso-b").getId();
    String scenarioA = UUID.randomUUID().toString();
    execute(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id) VALUES (?, ?, ?)",
        scenarioA,
        "workflow-child-iso-" + scenarioA,
        tenantA);
    workflowA = UUID.randomUUID().toString();
    execute(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
            + " workflow_scenario_id, tenant_id) VALUES (?, 'TEMPLATE', 1, ?, ?)",
        workflowA,
        scenarioA,
        tenantA);
  }

  @Nested
  @DisplayName("workflow_scope_rules")
  class ScopeRules {

    @Test
    @DisplayName("given its workflow's configuration, should list its own rule and not the other's")
    void given_workflowConfiguration_should_listOwnRuleAndNotOtherTenants() throws Exception {
      // Arrange
      String ownRule = seedScopeRule(tenantA);
      String foreignRule = seedScopeRule(tenantB);

      // Act
      String response = readConfiguration();

      // Assert
      assertTrue(response.contains(ownRule), "A's scope rule must be listed");
      assertFalse(response.contains(foreignRule), "B's scope rule must not be listed");
    }
  }

  @Nested
  @DisplayName("scope_variables")
  class ScopeVariables {

    @Test
    @DisplayName(
        "given its workflow's configuration, should list its own variable and not the other's")
    void given_workflowConfiguration_should_listOwnVariableAndNotOtherTenants() throws Exception {
      // Arrange
      String ownVariable = seedScopeVariable(tenantA);
      String foreignVariable = seedScopeVariable(tenantB);

      // Act
      String response = readConfiguration();

      // Assert
      assertTrue(response.contains(ownVariable), "A's scope variable must be listed");
      assertFalse(response.contains(foreignVariable), "B's scope variable must not be listed");
    }
  }

  // -- FIXTURES --

  private String readConfiguration() throws Exception {
    return mvc.perform(get(WORKFLOW_CONFIGURATION_URI, tenantA, workflowA))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String seedScopeRule(String tenantId) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO workflow_scope_rules (workflow_scope_rule_id, workflow_scope_rule_selected_mode,"
            + " workflow_scope_rule_source, workflow_scope_rule_value,"
            + " workflow_scope_rule_value_type, workflow_id, tenant_id)"
            + " VALUES (?, 'ALLOWLIST', 'ASSET', ?, 'ASSET_ID', ?, ?)",
        id,
        UUID.randomUUID().toString(),
        workflowA,
        tenantId);
    return id;
  }

  private String seedScopeVariable(String tenantId) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO scope_variables (scope_variable_id, scope_variable_key, scope_variable_type,"
            + " scope_variable_value, scope_variable_workflow, tenant_id)"
            + " VALUES (?, ?, 'Port', '445', ?, ?)",
        id,
        "variable-" + id,
        workflowA,
        tenantId);
    return id;
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
