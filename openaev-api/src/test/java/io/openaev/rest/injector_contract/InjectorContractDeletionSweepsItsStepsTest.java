package io.openaev.rest.injector_contract;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deleting an injector contract removes the chaining step templates that reference it, in the
 * contract's own tenant, whatever the caller's scope, on the single delete and on the threat
 * arsenal bulk delete.
 *
 * <p>The contract and the scenario whose logic map references it live in the default tenant. The
 * deletion is sent on the non-prefixed route by a caller whose scope excludes that tenant (a
 * platform admin who is a member of another tenant only, of no tenant at all, or of both tenants
 * while naming only the other one in {@code X-Tenant-Ids}) and by a member of the contract's
 * tenant: the outcome is the same.
 *
 * <p>The production active tables are armed, so the class follows what production enforces. The
 * rows are committed, as a real request sees them, and removed after each test. Quartz is off so
 * that no live trigger runs the chaining engine on them.
 */
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@TestInstance(PER_CLASS)
@DisplayName("Deleting an injector contract removes the step templates that reference it")
class InjectorContractDeletionSweepsItsStepsTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private String otherTenantId;

  @AfterAll
  void deleteOtherTenant() {
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  private record ContractInLogicMap(
      String contractId, String injectorId, String scenarioId, String stepTemplateId) {}

  /**
   * Commits a payload-less custom contract in the default tenant and a scenario whose logic map
   * holds one step template referencing it.
   */
  private ContractInLogicMap commitContractInLogicMap() {
    return commitContractInLogicMap(true);
  }

  /**
   * Same as {@link #commitContractInLogicMap()}, with a contract linked to no injector when {@code
   * withInjector} is false: an orphaned action, which the bulk delete removes for every caller.
   */
  private ContractInLogicMap commitContractInLogicMap(boolean withInjector) {
    TenantContext.clearCurrentTenant();
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
              contract.setCustom(true);
              if (!withInjector) {
                contract.clearInjectors();
              }
              InjectorContract savedContract =
                  injectorContractComposer.forInjectorContract(contract).persist().get();
              String injectorId =
                  withInjector ? savedContract.getInjectors().getFirst().getId() : null;

              Scenario savedScenario =
                  scenarioRepository.save(ScenarioFixture.createDefaultCrisisScenario());
              Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
              template.setScenario(savedScenario);
              Workflow savedTemplate = workflowRepository.save(template);
              Step stepTemplate = StepFixture.getDefaultStepTemplate();
              stepTemplate.setData(
                  "{\"inject_injector_contract\": {\"injector_contract_id\": \""
                      + savedContract.getId()
                      + "\"}}");
              stepTemplate.setWorkflow(savedTemplate);
              Step savedStepTemplate = stepRepository.save(stepTemplate);
              return new ContractInLogicMap(
                  savedContract.getId(),
                  injectorId,
                  savedScenario.getId(),
                  savedStepTemplate.getId());
            });
  }

  /** Sends the deletion with no tenant prefix, from the request thread's empty ambient tenant. */
  private int deleteContract(String contractId) throws Exception {
    return send(delete(INJECTOR_CONTRACT_URL + "/" + contractId));
  }

  /** Sends the threat arsenal bulk delete of one contract with no tenant prefix. */
  private int bulkDeleteContract(String contractId) throws Exception {
    return send(bulkDeleteRequest(contractId));
  }

  private MockHttpServletRequestBuilder bulkDeleteRequest(String contractId) {
    return post(THREAT_ARSENAL_URL + "/bulk-delete")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"injector_contract_ids_to_process\": [\"" + contractId + "\"]}");
  }

  private int send(MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return mvc.perform(request.with(csrf())).andReturn().getResponse().getStatus();
  }

  // Read outside the tenant inspector: the assertions are about the stored rows, not about what a
  // scope lets the test see.
  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private int count(String sql, String id) {
    return jdbc().queryForObject(sql, Integer.class, id);
  }

  private void remove(ContractInLogicMap rows) {
    jdbc().update("DELETE FROM steps WHERE step_id = ?", rows.stepTemplateId());
    jdbc().update("DELETE FROM workflows WHERE workflow_scenario_id = ?", rows.scenarioId());
    jdbc().update("DELETE FROM scenarios WHERE scenario_id = ?", rows.scenarioId());
    jdbc()
        .update(
            "DELETE FROM injectors_contracts WHERE injector_contract_id = ?", rows.contractId());
    if (rows.injectorId() != null) {
      jdbc().update("DELETE FROM injectors WHERE injector_id = ?", rows.injectorId());
    }
  }

  /**
   * Makes the current caller a member of another tenant than the contract's. The tenant is created
   * once, by the first caller that needs it: onboarding a tenant attaches the user who creates it.
   */
  private void joinOtherTenantOnly() {
    if (otherTenantId == null) {
      otherTenantId =
          new TransactionTemplate(transactionManager)
              .execute(
                  status -> {
                    try {
                      return tenantHelper.createTenant("contract-delete").getId();
                    } catch (Exception e) {
                      throw new IllegalStateException(e);
                    }
                  });
    }
    tenantHelper.attachCurrentUserToTenant(otherTenantId);
  }

  private void assertContractAndStepTemplateRemoved(int httpStatus, ContractInLogicMap rows) {
    assertThat(httpStatus).isEqualTo(200);
    assertThat(
            count(
                "SELECT count(*) FROM injectors_contracts WHERE injector_contract_id = ?",
                rows.contractId()))
        .as("the contract is deleted")
        .isZero();
    assertThat(count("SELECT count(*) FROM steps WHERE step_id = ?", rows.stepTemplateId()))
        .as("no step template keeps a reference to the deleted contract")
        .isZero();
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given no tenant membership should remove the step template")
    void given_noTenantMembership_should_removeTheStepTemplate() throws Exception {
      // Arrange
      ContractInLogicMap rows = commitContractInLogicMap();
      try {
        // Act
        int httpStatus = deleteContract(rows.contractId());

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should remove the step template")
    void given_scopeWithoutItsTenant_should_removeTheStepTemplate() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ContractInLogicMap rows = commitContractInLogicMap();
      try {
        // Act
        int httpStatus = deleteContract(rows.contractId());

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should remove the step template")
    void given_membershipOfItsTenant_should_removeTheStepTemplate() throws Exception {
      // Arrange
      ContractInLogicMap rows = commitContractInLogicMap();
      try {
        // Act
        int httpStatus = deleteContract(rows.contractId());

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a header naming another tenant only should remove the step template")
    void given_headerNamingAnotherTenantOnly_should_removeTheStepTemplate() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ContractInLogicMap rows = commitContractInLogicMap();
      try {
        // Act
        int httpStatus =
            send(
                delete(INJECTOR_CONTRACT_URL + "/" + rows.contractId())
                    .header("X-Tenant-Ids", otherTenantId));

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }
  }

  @Nested
  @DisplayName("Threat arsenal bulk delete")
  class BulkDelete {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given no tenant membership should remove the step template")
    void given_bulkDeleteWithNoTenantMembership_should_removeTheStepTemplate() throws Exception {
      // Arrange
      ContractInLogicMap rows = commitContractInLogicMap(false);
      try {
        // Act
        int httpStatus = bulkDeleteContract(rows.contractId());

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should remove the step template")
    void given_bulkDeleteWithScopeWithoutItsTenant_should_removeTheStepTemplate() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ContractInLogicMap rows = commitContractInLogicMap(false);
      try {
        // Act
        int httpStatus = bulkDeleteContract(rows.contractId());

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a header naming another tenant only should remove the step template")
    void given_bulkDeleteWithHeaderNamingAnotherTenantOnly_should_removeTheStepTemplate()
        throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ContractInLogicMap rows = commitContractInLogicMap(false);
      try {
        // Act
        int httpStatus =
            send(bulkDeleteRequest(rows.contractId()).header("X-Tenant-Ids", otherTenantId));

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should remove the step template")
    void given_bulkDeleteWithMembershipOfItsTenant_should_removeTheStepTemplate() throws Exception {
      // Arrange
      ContractInLogicMap rows = commitContractInLogicMap(false);
      try {
        // Act
        int httpStatus = bulkDeleteContract(rows.contractId());

        // Assert
        assertContractAndStepTemplateRemoved(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }
  }
}
