package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectExpectationTrace;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.service.attackpath.dto.AttackPathSecurityPlatformDTO;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * A1 — the execution-detail drawer exposes the security platforms that acted (prevention /
 * detection) and their alerts, resolved live from the execution's inject expectations.
 * Enterprise-gated.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path execution detail — security platforms (A1)")
class AttackPathSecurityPlatformsTest extends IntegrationTest {

  private static final String SIM = "SIM-SP";

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectExpectationTraceRepository injectExpectationTraceRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;

  // The security-platform resolution is Enterprise-gated (like the remediations); the test env has
  // no
  // active licence, so the gate is driven explicitly here to exercise the resolution itself.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private Tenant tenant;
  private Asset asset;
  private SecurityPlatform siem;

  @BeforeEach
  void seed() {
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-sp-tenant"));
    asset = assetRepository.save(AssetFixture.createDefaultAsset("dc-01"));

    siem = new SecurityPlatform();
    siem.setExternalReference("ext-siem");
    siem.setName("Splunk");
    siem.setSecurityPlatformType(SECURITY_PLATFORM_TYPE.SIEM);
    siem = securityPlatformRepository.save(siem);
  }

  /** Persists an expectation, SUCCESS, with a result from {@code sp}. */
  private BaseInjectExpectation persistExpectation(
      TechnicalInjectExpectation base, SecurityPlatform sp) {
    return persistExpectation(base, sp, base.getExpectedScore());
  }

  /** Persists an expectation with an explicit {@code score}, with a result from {@code sp}. */
  private BaseInjectExpectation persistExpectation(
      TechnicalInjectExpectation base, SecurityPlatform sp, Double score) {
    if (base.getAgent() == null) {
      base.setAsset(asset); // asset-scoped unless the fixture already carries an agent
    }
    base.setScore(score);
    base.setResults(
        new ArrayList<>(
            List.of(
                InjectExpectationResult.builder()
                    .sourceId(sp.getId())
                    .sourceType(sp.getSecurityPlatformType().name())
                    .sourceName(sp.getName())
                    .date(Instant.parse("2026-06-18T08:00:00Z").toString())
                    .score(score)
                    .build())));
    return injectExpectationRepository.save(base);
  }

  private String seedExecution(String injectId, String agentId) {
    Step step = StepFixture.getDefaultStepExecution(StepStatus.READY);
    step.setData("{\"inject_id\": \"" + injectId + "\"}");
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();

    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setStepId(step.getId());
    e.setAgentId(agentId);
    e.setSourceKind(agentId == null ? "INJECTOR" : "AGENT_ASSET");
    e.setTargetKind("ASSET");
    e.setTargetKey(asset.getId());
    e.setTargetAssetId(asset.getId());
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    return executionRepository.save(e).getId();
  }

  @Test
  @DisplayName("an asset-scoped detection expectation surfaces its platform and alerts")
  void assetExecution_listsDetectionPlatform_withAlerts() {
    Inject toPersist = InjectFixture.getDefaultInject();
    toPersist.setAssets(new ArrayList<>(List.of(asset)));
    Inject inject = injectRepository.save(toPersist);

    BaseInjectExpectation detection =
        persistExpectation(
            InjectExpectationFixture.createDetectionInjectExpectation(inject, null), siem);
    // two alerts on the detection expectation for this platform
    saveTrace(detection, siem, "Alert A", "http://siem/a");
    saveTrace(detection, siem, "Alert B", "http://siem/b");

    String executionId = seedExecution(inject.getId(), null);
    entityManager.flush();

    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    assertThat(d).isNotNull();
    assertThat(d.securityPlatforms()).hasSize(1);
    AttackPathSecurityPlatformDTO p = d.securityPlatforms().get(0);
    assertThat(p.bucket()).isEqualTo("detection");
    assertThat(p.platformType()).isEqualTo("SIEM");
    assertThat(p.platformName()).isEqualTo("Splunk");
    assertThat(p.status()).isEqualTo("SUCCESS");
    assertThat(p.alerts())
        .extracting(a -> a.title())
        .containsExactlyInAnyOrder("Alert A", "Alert B");
  }

  @Test
  @DisplayName("a platform that prevented is also surfaced as having detected")
  void preventedPlatform_alsoAppearsAsDetected() {
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    persistExpectation(
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null), siem);

    String executionId = seedExecution(inject.getId(), null);
    entityManager.flush();

    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    assertThat(d.securityPlatforms())
        .extracting(AttackPathSecurityPlatformDTO::bucket)
        .containsExactlyInAnyOrder("prevention", "detection");
    assertThat(d.securityPlatforms())
        .allSatisfy(
            p -> {
              assertThat(p.platformName()).isEqualTo("Splunk");
              assertThat(p.status()).isEqualTo("SUCCESS");
            });
  }

  @Test
  @DisplayName("an agent-scoped execution resolves its platforms by agent")
  void agentExecution_resolvesByAgent() {
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    Agent agent = agentRepository.save(AgentFixture.createAgent(asset, "ext-agent"));
    persistExpectation(
        InjectExpectationFixture.createDetectionInjectExpectation(inject, agent), siem);

    String executionId = seedExecution(inject.getId(), agent.getId());
    entityManager.flush();

    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    assertThat(d.securityPlatforms()).hasSize(1);
    assertThat(d.securityPlatforms().get(0).bucket()).isEqualTo("detection");
    assertThat(d.securityPlatforms().get(0).platformName()).isEqualTo("Splunk");
  }

  @Test
  @DisplayName(
      "prevented ⇒ detected overrides a contradictory 'not detected' for the same platform")
  void preventionSuccess_upgradesContradictoryDetectionToSuccess() {
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    // same platform: prevention SUCCESS but detection FAILED (contradictory) -> detected must win
    persistExpectation(
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null), siem);
    persistExpectation(
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null), siem, 0.0);

    String executionId = seedExecution(inject.getId(), null);
    entityManager.flush();

    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    AttackPathSecurityPlatformDTO detected =
        d.securityPlatforms().stream()
            .filter(p -> p.bucket().equals("detection"))
            .findFirst()
            .orElseThrow();
    assertThat(detected.status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("an inactive Enterprise licence yields no security platforms (no error)")
  void inactiveLicence_yieldsEmpty() {
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(false);
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    persistExpectation(
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null), siem);

    String executionId = seedExecution(inject.getId(), null);
    entityManager.flush();

    assertThat(graphService.executionDetail(SIM, executionId).securityPlatforms()).isEmpty();
  }

  @Test
  @DisplayName("a target with neither agent nor asset resolves to no platforms")
  void noScope_yieldsEmpty() {
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    persistExpectation(
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null), siem);

    Step step = StepFixture.getDefaultStepExecution(StepStatus.READY);
    step.setData("{\"inject_id\": \"" + inject.getId() + "\"}");
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setStepId(step.getId());
    e.setSourceKind("INJECTOR");
    e.setTargetKind("MANUAL");
    e.setTargetKey("raw-value"); // no agent, no asset
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    String executionId = executionRepository.save(e).getId();
    entityManager.flush();

    assertThat(graphService.executionDetail(SIM, executionId).securityPlatforms()).isEmpty();
  }

  private void saveTrace(
      BaseInjectExpectation expectation, SecurityPlatform sp, String name, String link) {
    InjectExpectationTrace t = new InjectExpectationTrace();
    t.setInjectExpectation(expectation);
    t.setSecurityPlatform(sp);
    t.setAlertName(name);
    t.setAlertLink(link);
    t.setAlertDate(Instant.parse("2026-06-18T08:00:00Z"));
    injectExpectationTraceRepository.save(t);
  }
}
