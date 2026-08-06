package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.NetworkTraffic;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionRemediation;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.DetectionRemediationFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.DetectionRemediationComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Phase A create (issue 5048, #203): at RUN, one EXECUTION row per resolved edge, tenant-attributed
 * from the current tenant context and keyed by the deterministic id, on the columns the read
 * consumes.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path Phase A: create EXECUTION rows")
@Import(AttackPathExecutionIngestionServiceTest.NudgeRecorder.class)
class AttackPathExecutionIngestionServiceTest extends IntegrationTest {

  /**
   * The simulation the verdict tests share; each uses its own tenant, so their counters are too.
   */
  private static final String SIM_EXPECTATION = "SIM-EXPECTATION";

  @Autowired private AttackPathExecutionIngestionService ingestionService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathExecutionRemediationRepository executionRemediationRepository;
  @Autowired private AttackPathVersionService versionService;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private DataSource dataSource;
  @Autowired private NudgeRecorder nudgeRecorder;

  /** Set by the verdict tests, whose tenant and rows are COMMITTED (they run untransacted). */
  private String verdictTenantId;

  @AfterEach
  void clearTenant() {
    if (verdictTenantId != null) {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      jdbc.update("DELETE FROM attackpath_execution WHERE tenant_id = ?", verdictTenantId);
      jdbc.update("DELETE FROM attackpath_graph_version WHERE tenant_id = ?", verdictTenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", verdictTenantId);
      verdictTenantId = null;
    }
    nudgeRecorder.clear();
    TenantContext.clearCurrentTenant();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("onRun persists remediation snapshots with plain text/HTML values")
  void onRunPersistsRemediationSnapshotsWithTextValues() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-remediation-values"));
    TenantContext.setCurrentTenant(tenant.getId());
    // This test runs untransacted (NOT_SUPPORTED) so its rows are COMMITTED; register the tenant
    // for the AfterEach purge (the tenant FK cascade clears the snapshot rows with it).
    verdictTenantId = tenant.getId();

    Endpoint endpoint = EndpointFixture.createEndpoint("corp-dc");
    endpoint.setHostname("corp-dc");
    endpoint.setIps(new String[] {"10.0.0.5"});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setTenant(tenant);

    // executors' PK is composite (executor_id, tenant_id): the executor must be attached to this
    // test's own tenant, not the default one, or the agent's FK to it fails to resolve.
    Agent agent =
        AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor(tenant.getId()));
    agent.setId("agt-rem-1");
    agent.setAsset(endpoint);
    agent.setExecutedByUser("agent-rem-1");
    endpointComposer.forEndpoint(endpoint).withAgent(agentComposer.forAgent(agent)).persist();

    var remediationPlain = DetectionRemediationFixture.createDefaultDetectionRemediation();
    remediationPlain.setValues("plain text remediation");
    var remediationHtml = DetectionRemediationFixture.createDefaultDetectionRemediation();
    remediationHtml.setValues("<p>test remed echo</p>");

    Payload payload =
        payloadComposer
            .forPayload(PayloadFixture.createDefaultCommand())
            .withDetectionRemediation(
                detectionRemediationComposer
                    .forDetectionRemediation(remediationPlain)
                    .withSecurityPlatform(
                        securityPlatformComposer.forSecurityPlatform(
                            SecurityPlatformFixture.createDefault("CrowdStrike Falcon", "EDR"))))
            .withDetectionRemediation(
                detectionRemediationComposer
                    .forDetectionRemediation(remediationHtml)
                    .withSecurityPlatform(
                        securityPlatformComposer.forSecurityPlatform(
                            SecurityPlatformFixture.createDefault("Microsoft Defender", "EDR"))))
            .persist()
            .get();

    Exercise exercise = new Exercise();
    exercise.setId("SIM-REMEDIATION");

    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(payload);

    Inject inject = InjectFixture.getDefaultInject();
    inject.setId("exec-rem-1");
    inject.setExercise(exercise);
    inject.setTenant(tenant);
    inject.setTitle("payload-with-remediation");
    inject.setInjectorContract(contract);
    inject.setAssets(List.of(endpoint));

    // Persist the step (within a workflow) before onRun so the remediation FK on steps(step_id)
    // is satisfied. step_workflow_id is NOT NULL, so the step must belong to a persisted workflow,
    // and the execution row freezes step.getStepTemplate().getId(), so link a template step too.
    StepComposer.Composer templateComposer =
        stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    Step step = StepFixture.getDefaultStepExecution(StepStatus.RUN);
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(templateComposer)
        .withStep(stepComposer.forStep(step).withStepTemplate(templateComposer))
        .persist();

    // Act / Assert
    // onRun nests a REQUIRES_NEW transaction via tenantTx.executeNew, which requires an active
    // ambient transaction; open one with execute(). The setup above is already committed
    // (NOT_SUPPORTED), so the nested transaction sees the tenant/steps.
    assertThatCode(
            () ->
                tenantTx.execute(
                    TxCtx.forTenant(tenant.getId()),
                    () -> ingestionService.onRun(inject, step, "cme")))
        .doesNotThrowAnyException();

    List<AttackPathExecutionRemediation> snapshots =
        executionRemediationRepository.findByStepId(step.getId());
    assertThat(snapshots).hasSize(2);
    assertThat(snapshots)
        .extracting(AttackPathExecutionRemediation::getValues)
        .containsExactlyInAnyOrder("plain text remediation", "<p>test remed echo</p>");
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

    Agent agent =
        AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor(tenant.getId()));
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

  /**
   * Builds the agent/endpoint/inject triple the target-resolution tests share, so each only has to
   * state the payload it exercises.
   */
  private Inject injectRunningPayload(Tenant tenant, String injectId, Payload payload) {
    Endpoint endpoint = EndpointFixture.createEndpoint("corp-dc");
    endpoint.setHostname("corp-dc");
    endpoint.setIps(new String[] {"10.0.0.5"});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setTenant(tenant);

    Agent agent =
        AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor(tenant.getId()));
    agent.setId("agt-1");
    agent.setAsset(endpoint);
    agent.setExecutedByUser("agent-1");
    endpointComposer.forEndpoint(endpoint).withAgent(agentComposer.forAgent(agent)).persist();

    Exercise exercise = new Exercise();
    exercise.setId("SIM-" + injectId);

    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(payload);

    Inject inject = InjectFixture.getDefaultInject();
    inject.setId(injectId);
    inject.setExercise(exercise);
    inject.setTenant(tenant);
    inject.setTitle(payload.getName());
    inject.setInjectorContract(contract);
    inject.setAssets(List.of(endpoint));
    return inject;
  }

  private static Step stepOf(String templateId, String stepId) {
    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    stepTemplate.setId(templateId);
    Step step = StepFixture.getDefaultStepTemplate();
    step.setId(stepId);
    step.setStepTemplate(stepTemplate);
    return step;
  }

  private static PayloadArgument argument(PrimitiveType type, String key, String defaultValue) {
    PayloadArgument argument = new PayloadArgument();
    argument.setType(type);
    argument.setKey(key);
    argument.setDefaultValue(defaultValue);
    return argument;
  }

  @Test
  @DisplayName(
      "A command whose host argument names the executing machine targets its endpoint, not a"
          + " 'localhost' node")
  void commandTargetingLoopbackStaysOnItsEndpoint() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-cmd-loopback"));
    TenantContext.setCurrentTenant(tenant.getId());

    Command command =
        (Command)
            PayloadFixture.createDefaultCommandWithArguments(
                List.of(
                    argument(PrimitiveType.Host, "host", "localhost"),
                    argument(PrimitiveType.Port, "port", "2020")));
    command.setName("echo");

    Inject inject = injectRunningPayload(tenant, "exec-loopback", command);
    String endpointId = inject.getAssets().getFirst().getId();

    // Act
    List<AttackPathExecution> rows =
        ingestionService.getAttackPathExecution(inject, stepOf("tmpl-1", "step-1"), "echo");

    // Assert
    // The command never left the machine it ran on, so it must stay attached to that endpoint.
    // Keying it on the literal "localhost" also merged every endpoint's local runs into one node.
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTargetKind()).isEqualTo("ASSET");
    assertThat(rows.getFirst().getTargetKey()).isEqualTo(endpointId);
    assertThat(rows.getFirst().getTargetHostname()).isEqualTo("corp-dc");
    assertThat(ingestionService.getExecutionIndex(inject, "agt-1"))
        .isEqualTo(rows.getFirst().getId());
  }

  @Test
  @DisplayName(
      "A command's target comes from the inject's own argument value, not the payload default")
  void commandTargetUsesInjectArgumentOverride() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-cmd-override"));
    TenantContext.setCurrentTenant(tenant.getId());

    Command command =
        (Command)
            PayloadFixture.createDefaultCommandWithArguments(
                List.of(argument(PrimitiveType.IPv4, "target_ip", "10.9.9.9")));
    command.setName("scan");

    Inject inject = injectRunningPayload(tenant, "exec-override", command);
    // The value this inject actually runs with, overriding the payload-level default.
    inject.setContent(JsonNodeFactory.instance.objectNode().put("target_ip", "192.168.56.11"));

    // Act
    List<AttackPathExecution> rows =
        ingestionService.getAttackPathExecution(inject, stepOf("tmpl-1", "step-1"), "scan");

    // Assert
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTargetKind()).isEqualTo("DISCOVERED");
    assertThat(rows.getFirst().getTargetKey()).isEqualTo("192.168.56.11");
    assertThat(ingestionService.getExecutionIndex(inject, "agt-1"))
        .isEqualTo(rows.getFirst().getId());
  }

  private static NetworkTraffic networkTrafficTo(String ipDst) {
    NetworkTraffic networkTraffic = new NetworkTraffic();
    networkTraffic.setId("network-traffic-id");
    networkTraffic.setName("beacon");
    networkTraffic.setIpSrc("10.0.0.5");
    networkTraffic.setIpDst(ipDst);
    networkTraffic.setPortSrc(4444);
    networkTraffic.setPortDst(443);
    networkTraffic.setProtocol("TCP");
    return networkTraffic;
  }

  @Test
  @DisplayName("A NetworkTraffic payload lands on the graph instead of writing no row at all")
  void networkTrafficPayloadIsIngested() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-network-traffic"));
    TenantContext.setCurrentTenant(tenant.getId());

    Inject inject = injectRunningPayload(tenant, "exec-network", networkTrafficTo("203.0.113.7"));

    // Act
    List<AttackPathExecution> rows =
        ingestionService.getAttackPathExecution(inject, stepOf("tmpl-1", "step-1"), null);

    // Assert
    // This payload type had no branch in the resolution switch, so the step wrote nothing and the
    // action never appeared on the attack path.
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTargetKind()).isEqualTo("DISCOVERED");
    assertThat(rows.getFirst().getTargetKey()).isEqualTo("203.0.113.7");
    assertThat(rows.getFirst().getSourceKind()).isEqualTo("AGENT");
    // The recomputed index must name the row that was persisted, or verdicts and terminal
    // traces written later through getExecutionIndex would miss it.
    assertThat(ingestionService.getExecutionIndex(inject, "agt-1"))
        .isEqualTo(rows.getFirst().getId());
  }

  @Test
  @DisplayName("A NetworkTraffic payload whose destination is loopback stays on its endpoint")
  void networkTrafficLoopbackStaysOnItsEndpoint() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-network-loopback"));
    TenantContext.setCurrentTenant(tenant.getId());

    Inject inject =
        injectRunningPayload(tenant, "exec-network-loop", networkTrafficTo("127.0.0.1"));
    String endpointId = inject.getAssets().getFirst().getId();

    // Act
    List<AttackPathExecution> rows =
        ingestionService.getAttackPathExecution(inject, stepOf("tmpl-1", "step-1"), null);

    // Assert
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTargetKind()).isEqualTo("ASSET");
    assertThat(rows.getFirst().getTargetKey()).isEqualTo(endpointId);
    assertThat(ingestionService.getExecutionIndex(inject, "agt-1"))
        .isEqualTo(rows.getFirst().getId());
  }

  @Test
  @DisplayName("A blank inject override does not name a target: the payload default still applies")
  void commandBlankOverrideFallsBackToPayloadDefault() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-cmd-blank"));
    TenantContext.setCurrentTenant(tenant.getId());

    Command command =
        (Command)
            PayloadFixture.createDefaultCommandWithArguments(
                List.of(argument(PrimitiveType.IPv4, "target_ip", "10.9.9.9")));
    command.setName("scan");

    Inject inject = injectRunningPayload(tenant, "exec-blank", command);
    // A whitespace-only value names nothing, so it must not suppress the payload default.
    inject.setContent(JsonNodeFactory.instance.objectNode().put("target_ip", "   "));

    // Act
    List<AttackPathExecution> rows =
        ingestionService.getAttackPathExecution(inject, stepOf("tmpl-1", "step-1"), "scan");

    // Assert
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTargetKind()).isEqualTo("DISCOVERED");
    assertThat(rows.getFirst().getTargetKey()).isEqualTo("10.9.9.9");
  }

  @Test
  @DisplayName(
      "The persisted target value is trimmed, so stray whitespace never forks a second node")
  void commandTargetValueIsTrimmedBeforePersisting() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-cmd-trim"));
    TenantContext.setCurrentTenant(tenant.getId());

    Command command =
        (Command)
            PayloadFixture.createDefaultCommandWithArguments(
                List.of(argument(PrimitiveType.IPv4, "target_ip", "10.9.9.9")));
    command.setName("scan");

    Inject inject = injectRunningPayload(tenant, "exec-trim", command);
    inject.setContent(JsonNodeFactory.instance.objectNode().put("target_ip", " 192.168.56.11  "));

    // Act
    List<AttackPathExecution> rows =
        ingestionService.getAttackPathExecution(inject, stepOf("tmpl-1", "step-1"), "scan");

    // Assert
    // The id is keyed on the target value: an untrimmed " 192.168.56.11  " would be a distinct
    // node from "192.168.56.11", and would diverge from the loopback check, which trims.
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTargetKey()).isEqualTo("192.168.56.11");
    assertThat(ingestionService.getExecutionIndex(inject, "agt-1"))
        .isEqualTo(rows.getFirst().getId());
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

    Agent agent =
        AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor(tenant.getId()));
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
    assertThat(executionRepository.countExecutions("SIM-IDEM")).isEqualTo(1);
    assertThat(
            executionRepository.findById(
                AttackPathIds.executionNode("exec-idem", endpointId, "agt-1")))
        .isPresent();
  }

  @Test
  @DisplayName(
      "Team-targeted (human-in-the-loop) inject renders the team and each enabled recipient")
  void teamTargetedInjectRendersTeamAndRecipients() {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-team-target"));
    TenantContext.setCurrentTenant(tenant.getId());

    Exercise exercise = new Exercise();
    exercise.setId("SIM-TEAM");

    User recipient = new User();
    recipient.setId("user-sam");
    recipient.setEmail("sam@corp.example");

    Team team = new Team();
    team.setId("team-ai-1");
    team.setName("AI target sam@corp.example");
    ExerciseTeamUser etu = new ExerciseTeamUser();
    etu.setExercise(exercise);
    etu.setTeam(team);
    etu.setUser(recipient);
    team.getExerciseTeamUsers().add(etu);

    Injector injector = new Injector();
    injector.setId("injector-email");
    injector.setType("openaev_email");
    injector.setName("Email");

    // An email injector needs no executor and targets a TEAM (no assets, no target_selector).
    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setNeedsExecutor(false);

    Inject inject = InjectFixture.getDefaultInject();
    inject.setId("inject-phish-1");
    inject.setExercise(exercise);
    inject.setTenant(tenant);
    inject.setTitle("Spearphishing link");
    inject.setInjectorContract(contract);
    inject.setInjector(injector);
    inject.setContent(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
    inject.setTeams(List.of(team));

    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    stepTemplate.setId("tmpl-team");
    Step step = StepFixture.getDefaultStepTemplate();
    step.setId("step-team");
    step.setStepTemplate(stepTemplate);

    // Act
    List<AttackPathExecution> rows = ingestionService.getAttackPathExecution(inject, step, null);

    // Assert: one TEAM row (injector -> team) and one PERSON row (team -> recipient).
    assertThat(rows).hasSize(2);

    AttackPathExecution teamRow =
        rows.stream().filter(r -> "TEAM".equals(r.getTargetKind())).findFirst().orElseThrow();
    assertThat(teamRow.getId())
        .isEqualTo(AttackPathIds.executionNode("inject-phish-1", "team-ai-1", "injector-email"));
    assertThat(teamRow.getSourceKind()).isEqualTo("INJECTOR");
    assertThat(teamRow.getTargetKey()).isEqualTo("team-ai-1");
    assertThat(teamRow.getTargetHostname()).isEqualTo("AI target sam@corp.example");
    assertThat(teamRow.getSimulationId()).isEqualTo("SIM-TEAM");

    AttackPathExecution personRow =
        rows.stream().filter(r -> "PERSON".equals(r.getTargetKind())).findFirst().orElseThrow();
    assertThat(personRow.getId())
        .isEqualTo(AttackPathIds.executionNode("inject-phish-1", "user-sam", "team-ai-1"));
    // The person hangs off the team, so the team is this row's SOURCE (injector -> team -> person).
    assertThat(personRow.getSourceKind()).isEqualTo("TEAM");
    assertThat(personRow.getSourceAssetId()).isEqualTo("team-ai-1");
    assertThat(personRow.getTargetKey()).isEqualTo("user-sam");
    assertThat(personRow.getTargetHostname()).isEqualTo("sam@corp.example");
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
    assertThat(executionRepository.countExecutions("SIM-NOCONTRACT")).isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("given_mixedExpectationResults_should_updateExecutionWithHighestPriorityLabels")
  void given_mixedExpectationResults_should_updateExecutionWithHighestPriorityLabels() {
    // Arrange
    Tenant tenant = verdictTenant("ap-expectation-priority");
    String executionId = "exec-priority-1";
    executionRepository.save(createExecutionRow(executionId, tenant));

    // Act
    updateExpectations(
        tenant,
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

    // Assert
    AttackPathExecution updated = executionRepository.findById(executionId).orElseThrow();
    assertThat(updated.getPreventionStatus()).isEqualTo("Prevented");
    assertThat(updated.getDetectionStatus()).isEqualTo("Partially Detected");
    assertThat(updated.getVulnerabilityStatus()).isEqualTo("Pending");
    // The stamp is what carries the verdict into the delta read: an unstamped row is invisible to
    // every polling client, which is the frozen-at-pending failure this feature removes (#6647).
    assertThat(updated.getRowVersion()).isEqualTo(currentVersion());
    assertThat(currentVersion()).isPositive();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("replaying the identical expectation result changes nothing, version stamp included")
  void replayingTheSameResultIsANoOp() {
    Tenant tenant = verdictTenant("ap-expectation-replay");
    String executionId = "exec-replay-1";
    executionRepository.save(createExecutionRow(executionId, tenant));

    updateExpectations(tenant, executionId, prevention("Prevented"));
    long stampedVersion = executionRepository.findById(executionId).orElseThrow().getRowVersion();

    updateExpectations(tenant, executionId, prevention("Prevented"));

    AttackPathExecution replayed = executionRepository.findById(executionId).orElseThrow();
    assertThat(replayed.getPreventionStatus()).isEqualTo("Prevented");
    // The guard matched zero rows, so the row keeps its version and no client is told anything
    // changed — even though the batch itself bumped the simulation's counter.
    assertThat(replayed.getRowVersion()).isEqualTo(stampedVersion);
    assertThat(currentVersion()).isGreaterThan(stampedVersion);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("a verdict that later changes is written again, with a newer version")
  void aChangedVerdictIsWrittenAgain() {
    Tenant tenant = verdictTenant("ap-expectation-change");
    String executionId = "exec-change-1";
    executionRepository.save(createExecutionRow(executionId, tenant));

    updateExpectations(tenant, executionId, prevention("Not Prevented"));
    long firstVersion = executionRepository.findById(executionId).orElseThrow().getRowVersion();

    updateExpectations(tenant, executionId, prevention("Prevented"));

    AttackPathExecution updated = executionRepository.findById(executionId).orElseThrow();
    assertThat(updated.getPreventionStatus()).isEqualTo("Prevented");
    assertThat(updated.getRowVersion()).isGreaterThan(firstVersion);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("only a write that changed rows nudges a client")
  void onlyAChangedWriteNudges() {
    // The nudge is what tells an open view to fetch its delta now. The engine replays an execution
    // event's expectation results by design, so publishing on a replay would put one event per
    // replay
    // on the stream's shared executor — whose bounded queue drops its oldest entries, including the
    // ping every client's health check reads. Hence: no rows changed, no nudge.
    Tenant tenant = verdictTenant("ap-expectation-nudge");
    String executionId = "exec-nudge-1";
    executionRepository.save(createExecutionRow(executionId, tenant));

    updateExpectations(tenant, executionId, prevention("Prevented"));
    assertThat(publishedNudges(tenant))
        .as("the first write changed the verdict, so it announces itself")
        .isEqualTo(1);

    updateExpectations(tenant, executionId, prevention("Prevented"));
    assertThat(publishedNudges(tenant))
        .as("the replay matched zero rows: still one nudge in total")
        .isEqualTo(1);

    updateExpectations(tenant, executionId, prevention("Not Prevented"));
    assertThat(publishedNudges(tenant)).as("a real change announces itself again").isEqualTo(2);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("no expectation result writes nothing and does not bump the version")
  void anEmptyResultSetIsNotVersioned() {
    Tenant tenant = verdictTenant("ap-expectation-empty");
    String executionId = "exec-empty-1";
    executionRepository.save(createExecutionRow(executionId, tenant));

    tenantTx.execute(
        TxCtx.forTenant(tenant.getId()),
        () -> ingestionService.updateExpectationByExecutionIndex(inject(tenant), Map.of()));

    AttackPathExecution untouched = executionRepository.findById(executionId).orElseThrow();
    assertThat(untouched.getPreventionStatus()).isNull();
    assertThat(untouched.getRowVersion()).isZero();
    assertThat(versionService.current(SIM_EXPECTATION, List.of(tenant.getId()))).isEmpty();
  }

  // -- verdict helpers --

  /**
   * How many nudges this tenant's simulation has published so far. Counted through a listener
   * rather than a mock so the assertion covers the real path: the event must survive the
   * transaction's commit to be delivered at all.
   */
  private int publishedNudges(Tenant tenant) {
    return (int)
        nudgeRecorder.events().stream()
            .filter(e -> SIM_EXPECTATION.equals(e.simulationId()))
            .filter(e -> tenant.getId().equals(e.tenantId()))
            .count();
  }

  /**
   * Records the nudges published during a test, after commit, exactly as the stream receives them.
   */
  @Component
  static class NudgeRecorder {
    private final List<AttackPathVersionEvent> events = new CopyOnWriteArrayList<>();

    @TransactionalEventListener
    void onNudge(AttackPathVersionEvent event) {
      events.add(event);
    }

    List<AttackPathVersionEvent> events() {
      return events;
    }

    void clear() {
      events.clear();
    }
  }

  /**
   * Calls the service the way the chaining engine does: from inside an ambient tenant-scoped
   * transaction. The write opens its own {@code executeNew} boundary so it commits independently of
   * the run, and that primitive refuses to run at the top level — in production the ambient
   * transaction is the one {@code StepEventService} opens per event.
   */
  private void updateExpectations(
      Tenant tenant,
      String executionId,
      AttackPathExecutionIngestionService.ExecutionExpectationResults results) {
    tenantTx.execute(
        TxCtx.forTenant(tenant.getId()),
        () ->
            ingestionService.updateExpectationByExecutionIndex(
                inject(tenant), Map.of(executionId, results)));
  }

  /** The inject as the chaining seam hands it over: its tenant and simulation are read. */
  private static Inject inject(Tenant tenant) {
    Inject inject = new Inject();
    inject.setTenant(tenant);
    Exercise exercise = new Exercise();
    exercise.setId(SIM_EXPECTATION);
    inject.setExercise(exercise);
    return inject;
  }

  private static AttackPathExecutionIngestionService.ExecutionExpectationResults prevention(
      String result) {
    return new AttackPathExecutionIngestionService.ExecutionExpectationResults(
        List.of(expectationResult(result)), List.of(), List.of());
  }

  /**
   * A committed tenant for the verdict tests, which run outside the test transaction (the write
   * commits in its own scope). Each keeps its own simulation counter and rows, cleaned up after.
   */
  private Tenant verdictTenant(String name) {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant(name));
    TenantContext.setCurrentTenant(tenant.getId());
    verdictTenantId = tenant.getId();
    return tenant;
  }

  private long currentVersion() {
    return versionService.current(SIM_EXPECTATION, List.of(verdictTenantId)).orElseThrow();
  }

  private static AttackPathExecution createExecutionRow(String id, Tenant tenant) {
    AttackPathExecution execution = new AttackPathExecution();
    execution.setId(id);
    execution.setTenant(tenant);
    execution.setSimulationId(SIM_EXPECTATION);
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
