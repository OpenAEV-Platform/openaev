package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathExecutionRemediation;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.utils.fixtures.DetectionRemediationFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.DetectionRemediationComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * The execution Result & Terminal detail read (issue 5048). Reading one execution returns its
 * header (payload/agent/privilege), its result (target, prevention/detection status, findings), and
 * its terminal (command, output) from the frozen snapshot, with the linked credential secret masked
 * in the command, the output, and the finding value.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path execution Result & Terminal detail read")
class AttackPathExecutionDetailTest extends IntegrationTest {

  private static final String SIM = "SIM-DETAIL";

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;

  // The remediation mapping is Enterprise-gated. The test environment has no active licence, so the
  // gate is driven explicitly here: that is the only way to exercise the resolution itself rather
  // than the degraded empty-list path.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private Tenant tenant;
  private String executionId;
  private String payloadId;
  private String stepId;

  @BeforeEach
  void seed() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-detail-tenant"));
    // The remediation read is a Spring Data derived query subject to the tenant filter (unlike
    // findById on the execution). Scope the thread so findByStepId resolves the seeded snapshot.
    TenantContext.setCurrentTenant(tenant.getId());

    // A real persisted step: the remediation snapshot table has an FK step_id -> steps, and the
    // read resolves remediations by this step id.
    Step step = StepFixture.getDefaultStepExecution(StepStatus.READY);
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();
    stepId = step.getId();

    // A real injector contract carrying an ATT&CK technique, referenced by the row's external id:
    // the read resolves the techniques from it for the drawer's chips.
    InjectorContract contract =
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createDefaultInjectorContractWithExternalId(
                    "contract-ext-1"))
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withAttackPattern(
                attackPatternComposer.forAttackPattern(
                    AttackPatternFixture.createAttackPatternsWithExternalId("T1046")))
            .persist()
            .get();

    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setStepId(stepId);
    // A real payload carrying a detection remediation: its values are snapshotted at step execution
    // and later served from the attack-path remediation store.
    Payload payload =
        payloadComposer
            .forPayload(PayloadFixture.createDefaultCommand())
            .withDetectionRemediation(
                detectionRemediationComposer
                    .forDetectionRemediation(
                        DetectionRemediationFixture.createDefaultDetectionRemediation())
                    .withSecurityPlatform(
                        securityPlatformComposer.forSecurityPlatform(
                            SecurityPlatformFixture.createDefault("CrowdStrike Falcon", "EDR"))))
            .persist()
            .get();
    payloadId = payload.getId();

    e.setContractExternalId(contract.getExternalId());
    e.setPayloadId(payloadId);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector("hydra");
    e.setTargetKind("ASSET");
    e.setTargetAssetId("dc-01");
    e.setTargetKey("dc-01");
    e.setTargetHostname("CORP-DC-01");
    e.setTargetIp("10.0.0.1");
    e.setTargetPlatform("Linux");
    e.setPayloadName("hydra-payload");
    e.setAgentName("agent-1");
    e.setAgentPrivilege("user");
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    e.setPreventionStatus("Not Prevented");
    e.setDetectionStatus("Detected");
    e.setCommand("hydra -l admin -p secret123 ssh://10.0.0.1");
    e.setTerminalOutput("[+] login: admin  password: secret123\n[+] valid credential found");
    executionId = executionRepository.save(e).getId();

    // The read serves remediations from the snapshot table (findByStepId), not the live payload.
    AttackPathExecutionRemediation remediation = new AttackPathExecutionRemediation();
    remediation.setId("apr-detail-1");
    remediation.setTenant(tenant);
    remediation.setStepId(stepId);
    remediation.setValues("remediation values");
    remediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.HUMAN);
    remediation.setSecurityPlatformId("sp-detail-1");
    entityManager.persist(remediation);

    linkFinding("credentials", "admin:secret123");
    linkFinding("cve", "CVE-2026-1");
    entityManager.flush();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  private void linkFinding(String type, String value) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type);
    f.setValue(value);
    f.setEndpointId("dc-01");
    f.setEndpointKey("dc-01");
    String findingId = findingRepository.save(f).getId();

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(executionId);
    link.setFindingId(findingId);
    entityManager.persist(link);
  }

  @Test
  @DisplayName(
      "returns header, result, and terminal with the secret masked in command, output, and finding")
  void returnsDetailWithMaskedSecret() {
    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    assertThat(d).isNotNull();
    // header
    assertThat(d.stepId()).isEqualTo(stepId);
    assertThat(d.payloadName()).isEqualTo("hydra-payload");
    assertThat(d.payloadId()).isEqualTo(payloadId);
    // the run's contract resolves to its ATT&CK techniques (the drawer's chips)
    assertThat(d.attackPatterns()).hasSize(1);
    assertThat(d.attackPatterns().get(0).externalId()).isEqualTo("T1046");
    assertThat(d.agentName()).isEqualTo("agent-1");
    assertThat(d.agentPrivilege()).isEqualTo("user");
    // result
    assertThat(d.targetHostname()).isEqualTo("CORP-DC-01");
    assertThat(d.targetIp()).isEqualTo("10.0.0.1");
    assertThat(d.endpointKey()).isEqualTo("dc-01");
    assertThat(d.preventionStatus()).isEqualTo("Not Prevented");
    assertThat(d.detectionStatus()).isEqualTo("Detected");
    // the credential is masked (username kept), the cve is untouched
    assertThat(d.findings())
        .satisfiesExactlyInAnyOrder(
            item -> {
              assertThat(item.type()).isEqualTo("credentials");
              assertThat(item.value()).startsWith("admin:").doesNotContain("secret123");
            },
            item -> {
              assertThat(item.type()).isEqualTo("cve");
              assertThat(item.value()).isEqualTo("CVE-2026-1");
            });
    // terminal: the linked secret is masked in the command and the output
    assertThat(d.command()).contains("hydra -l admin").doesNotContain("secret123");
    assertThat(d.terminalOutput()).doesNotContain("secret123");
  }

  @Test
  @DisplayName("resolves the inject link from the durable step the row is keyed by")
  void resolvesInjectIdFromTheStep() {
    // The engine freezes inject_id into step_data at run (InjectExecutionStep.setInjectId); the
    // read
    // resolves the "Action details" inject link from that durable step, not from the dropped
    // column.
    Step step = StepFixture.getDefaultStepExecution(StepStatus.READY);
    step.setData("{\"inject_id\": \"inj-detail-1\"}");
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();
    String stepId = step.getId();

    AttackPathExecution linked = new AttackPathExecution();
    linked.setTenant(tenant);
    linked.setSimulationId(SIM);
    linked.setStepId(stepId);
    linked.setSourceKind("INJECTOR");
    linked.setTargetKind("ASSET");
    linked.setTargetKey("dc-01");
    linked.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    String linkedExecutionId = executionRepository.save(linked).getId();
    entityManager.flush();

    assertThat(graphService.executionDetail(SIM, linkedExecutionId).injectId())
        .isEqualTo("inj-detail-1");
  }

  @Test
  @DisplayName("an unknown execution or a different simulation returns null (not found)")
  void unknownExecutionOrSimulationIsNull() {
    assertThat(graphService.executionDetail(SIM, "does-not-exist")).isNull();
    assertThat(graphService.executionDetail("OTHER-SIM", executionId)).isNull();
  }

  @Test
  @DisplayName(
      "with an active Enterprise licence, the remediations of the payload that ran are returned")
  void returnsDetectionRemediationsWhenEnterpriseActive() {
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);

    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    assertThat(d.detectionRemediations()).hasSize(1);
  }

  @Test
  @DisplayName("without an active Enterprise licence, the remediations are omitted")
  void omitsDetectionRemediationsWhenEnterpriseInactive() {
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(false);

    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    assertThat(d.detectionRemediations()).isEmpty();
  }

  // A network injector's (NetExec, Nmap…) own trace always redacts every flag with a blanket "***";
  // injectorCommandLine() un-redacts the flags it recognizes from the inject's resolved content,
  // partially masking password/hash rather than exposing them in full. These tests target the pure
  // helpers directly (reflection, like InjectExecutionStepTest#getCommand) rather than seeding a full
  // Inject/InjectStatus/ExecutionTrace tree through the ORM.

  @Test
  @DisplayName("findCommandTraceMessage skips the 'published' boilerplate trace")
  void findCommandTraceMessageSkipsPublishedBoilerplate() {
    ExecutionTrace published = new ExecutionTrace();
    published.setMessage("The inject has been published, waiting to be consumed");
    ExecutionTrace command = new ExecutionTrace();
    command.setMessage("netexec smb 10.0.0.1 -u admin -p ***");

    String result =
        ReflectionTestUtils.invokeMethod(
            graphService, "findCommandTraceMessage", List.of(published, command));

    assertThat(result).isEqualTo("netexec smb 10.0.0.1 -u admin -p ***");
  }

  @Test
  @DisplayName("findCommandTraceMessage returns the first trace when it is not the boilerplate")
  void findCommandTraceMessageReturnsFirstWhenNotBoilerplate() {
    ExecutionTrace command = new ExecutionTrace();
    command.setMessage("nmap -p 22,80,443 10.0.0.1");

    String result =
        ReflectionTestUtils.invokeMethod(graphService, "findCommandTraceMessage", List.of(command));

    assertThat(result).isEqualTo("nmap -p 22,80,443 10.0.0.1");
  }

  @Test
  @DisplayName("unmaskCommandLine partially reveals the password and shows the username in full")
  void unmaskCommandLineRevealsRecognizedFlags() {
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put("username", "admin");
    content.put("password", "secret123");

    String result =
        ReflectionTestUtils.invokeMethod(
            graphService, "unmaskCommandLine", "netexec smb 10.0.0.1 -u *** -p ***", content);

    assertThat(result).isEqualTo("netexec smb 10.0.0.1 -u admin -p s*******3");
    assertThat(result).doesNotContain("secret123");
  }

  @Test
  @DisplayName("unmaskCommandLine leaves an unrecognized or unresolved flag exactly as redacted")
  void unmaskCommandLineLeavesUnresolvedFlagsRedacted() {
    ObjectNode content = JsonNodeFactory.instance.objectNode();
    content.put("username", "admin");

    String result =
        ReflectionTestUtils.invokeMethod(
            graphService,
            "unmaskCommandLine",
            "netexec smb 10.0.0.1 -u *** -p *** --unknown-flag ***",
            content);

    assertThat(result).isEqualTo("netexec smb 10.0.0.1 -u admin -p *** --unknown-flag ***");
  }
}
