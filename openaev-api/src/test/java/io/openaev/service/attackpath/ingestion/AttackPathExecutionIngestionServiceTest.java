package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase A create (issue 5048, #203): at RUN, one EXECUTION row per resolved edge, tenant-attributed
 * from the current tenant context and keyed by the deterministic id, on the columns the read
 * consumes.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path Phase A: create EXECUTION rows")
class AttackPathExecutionIngestionServiceTest extends IntegrationTest {

  @Autowired private AttackPathExecutionIngestionService ingestionService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("Agent-based run generates and persists one tenant-scoped execution row")
  void generatesAndPersistsRow() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-ingest-tenant"));
    TenantContext.setCurrentTenant(tenant.getId());

    Endpoint endpoint = EndpointFixture.createEndpoint("corp-dc");
    endpoint.setHostname("corp-dc");
    endpoint.setIps(new String[] {"10.0.0.5"});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setTenant(tenant);

    Agent agent = AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor());
    agent.setId("agt-1");
    agent.setAsset(endpoint);
    agent.setExecutedByUser("agent-1");

    endpointComposer.forEndpoint(endpoint).withAgent(agentComposer.forAgent(agent)).persist();
    String endpointId = endpoint.getId();

    Exercise exercise = new Exercise();
    exercise.setId("SIM-INGEST");

    Command command = (Command) PayloadFixture.createDefaultCommand();
    command.setName("crackmapexec");

    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(command);

    Inject inject = InjectFixture.getDefaultInject();
    inject.setId("exec-1");
    inject.setExercise(exercise);
    inject.setTenant(tenant);
    inject.setTitle("crackmapexec");
    inject.setInjectorContract(contract);
    inject.setAssets(List.of(endpoint));

    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    stepTemplate.setId("tmpl-1");
    Step step = StepFixture.getDefaultStepTemplate();
    step.setId("step-1");
    step.setStepTemplate(stepTemplate);

    // Act
    List<AttackPathExecution> rows = ingestionService.getAttackPathExecution(inject, step, "cme");
    ingestionService.persistExecution(rows);

    // Assert
    String id = AttackPathIds.executionNode("exec-1", endpointId, "agt-1");
    AttackPathExecution row = executionRepository.findById(id).orElseThrow();

    assertThat(row.getTenant().getId()).isEqualTo(tenant.getId());
    assertThat(row.getSimulationId()).isEqualTo("SIM-INGEST");
    assertThat(row.getStepId()).isEqualTo("step-1");
    assertThat(row.getStepTemplateId()).isEqualTo("tmpl-1");
    assertThat(row.getSourceKind()).isEqualTo("AGENT");
    assertThat(row.getSourceAssetId()).isEqualTo(endpointId);
    assertThat(row.getSourceHostname()).isEqualTo("corp-dc");
    assertThat(row.getSourceIp()).isEqualTo("10.0.0.5");
    assertThat(row.getSourcePlatform()).isEqualTo("Windows");
    assertThat(row.getTargetKind()).isEqualTo("ASSET");
    assertThat(row.getTargetAssetId()).isEqualTo(endpointId);
    assertThat(row.getTargetKey()).isEqualTo(endpointId);
    assertThat(row.getTargetHostname()).isEqualTo("corp-dc");
    assertThat(row.getTargetPlatform()).isEqualTo("Windows");
    assertThat(row.getAgentId()).isEqualTo("agt-1");
    assertThat(row.getAgentName()).isEqualTo("OpenAEV Agent");
    assertThat(row.getAgentPrivilege()).isEqualTo("admin");
    assertThat(row.getExecutedAt()).isNotNull();
    assertThat(row.getPayloadName()).isEqualTo("crackmapexec");
  }

  @Test
  @DisplayName(
      "Re-running the same edge converges on the same row (deterministic id, no duplicate)")
  void idempotentOnSameKey() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-ingest-idem"));
    TenantContext.setCurrentTenant(tenant.getId());

    Endpoint endpoint = EndpointFixture.createEndpoint("corp-dc");
    endpoint.setHostname("corp-dc");
    endpoint.setIps(new String[] {"10.0.0.5"});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setTenant(tenant);

    Agent agent = AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor());
    agent.setId("agt-1");
    agent.setAsset(endpoint);
    agent.setExecutedByUser("agent-1");

    endpointComposer.forEndpoint(endpoint).withAgent(agentComposer.forAgent(agent)).persist();
    String endpointId = endpoint.getId();

    Exercise exercise = new Exercise();
    exercise.setId("SIM-IDEM");

    Command command = (Command) PayloadFixture.createDefaultCommand();
    command.setName("crackmapexec");

    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(command);

    Inject inject = InjectFixture.getDefaultInject();
    inject.setId("exec-idem");
    inject.setExercise(exercise);
    inject.setTenant(tenant);
    inject.setTitle("crackmapexec");
    inject.setInjectorContract(contract);
    inject.setAssets(List.of(endpoint));

    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    stepTemplate.setId("tmpl-1");
    Step step = StepFixture.getDefaultStepTemplate();
    step.setId("step-1");
    step.setStepTemplate(stepTemplate);

    // Act
    List<AttackPathExecution> rows = ingestionService.getAttackPathExecution(inject, step, "cme");
    ingestionService.persistExecution(rows);
    ingestionService.persistExecution(rows);

    // Assert
    // The deterministic id makes the second write an update, not a new row.
    assertThat(executionRepository.count()).isEqualTo(1);
    assertThat(
            executionRepository.findById(
                AttackPathIds.executionNode("exec-idem", endpointId, "agt-1")))
        .isPresent();
  }

  @Test
  @DisplayName("getAttackPathExecution returns empty when injector contract is missing")
  void getAttackPathExecutionReturnsEmptyWithoutInjectorContract() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-onrun-tenant"));
    TenantContext.setCurrentTenant(tenant.getId());

    Exercise exercise = new Exercise();
    exercise.setId("SIM-NOCONTRACT");

    Inject inject = new Inject();
    inject.setId("inj-no-contract");
    inject.setExercise(exercise);
    inject.setTenant(tenant);

    Step template = new Step();
    template.setId("tmpl-1");
    Step step = new Step();
    step.setId("step-1");
    step.setStepTemplate(template);

    // Act
    List<AttackPathExecution> rows = ingestionService.getAttackPathExecution(inject, step, "cme");

    // Assert
    assertThat(rows).isEmpty();
    assertThat(executionRepository.count()).isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("given_mixedExpectationResults_should_updateExecutionWithHighestPriorityLabels")
  void given_mixedExpectationResults_should_updateExecutionWithHighestPriorityLabels() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-expectation-priority"));
    TenantContext.setCurrentTenant(tenant.getId());
    String executionId = "exec-priority-1";
    executionRepository.save(createExecutionRow(executionId, tenant));

    Inject inject = new Inject();
    inject.setTenant(tenant);

    Map<String, AttackPathExecutionIngestionService.ExecutionExpectationResults>
        expectationResults =
            Map.of(
                executionId,
                new AttackPathExecutionIngestionService.ExecutionExpectationResults(
                    List.of(
                        expectationResult("Not Prevented"),
                        expectationResult("Pending"),
                        expectationResult("Partially Prevented"),
                        expectationResult("Prevented")),
                    List.of(
                        expectationResult("Not Detected"),
                        expectationResult("Pending"),
                        expectationResult("Partially Detected")),
                    List.of(expectationResult("Vulnerable"), expectationResult("Pending"))));

    // Act
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> ingestionService.updateExpectationByExecutionIndex(inject, expectationResults));

    // Assert
    AttackPathExecution updated = executionRepository.findById(executionId).orElseThrow();
    assertThat(updated.getPreventionStatus()).isEqualTo("Prevented");
    assertThat(updated.getDetectionStatus()).isEqualTo("Partially Detected");
    assertThat(updated.getVulnerabilityStatus()).isEqualTo("Pending");
  }

  private static AttackPathExecution createExecutionRow(String id, Tenant tenant) {
    AttackPathExecution execution = new AttackPathExecution();
    execution.setId(id);
    execution.setTenant(tenant);
    execution.setSimulationId("SIM-EXPECTATION");
    execution.setSourceKind("AGENT");
    execution.setTargetKind("ASSET");
    execution.setTargetKey("target-key-1");
    execution.setExecutedAt(java.time.Instant.now());
    return execution;
  }

  private static InjectExpectationResult expectationResult(String result) {
    InjectExpectationResult expectationResult = new InjectExpectationResult();
    expectationResult.setResult(result);
    return expectationResult;
  }
}
