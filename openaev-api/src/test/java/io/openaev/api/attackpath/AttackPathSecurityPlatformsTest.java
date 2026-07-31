package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Inject;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionCollector;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionCollectorRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.service.attackpath.dto.AttackPathSecurityPlatformDTO;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
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
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AttackPathExecutionCollectorRepository executionCollectorRepository;
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

  private void saveCollectorRow(
      String executionId, String expectationType, String status, String alertsJson, Double score) {
    String resolvedSourceType = siem.getSecurityPlatformType().name();
    AttackPathExecutionCollector row = new AttackPathExecutionCollector();
    row.setId(executionId + "-" + expectationType + "-" + resolvedSourceType);
    row.setTenant(tenant);
    row.setSimulationId(SIM);
    row.setExecutionId(executionId);
    row.setExpectationType(expectationType);
    row.setSourceId(siem.getId());
    row.setSourceType(resolvedSourceType);
    row.setSourceName(siem.getName());
    row.setSourceAssetId(siem.getId());
    row.setResultStatusLabel(status);
    row.setDetectionTime(Instant.parse("2026-06-18T08:00:00Z").toString());
    row.setAlerts(toJsonNode(alertsJson));
    row.setResultScore(score);
    row.setResultDate(Instant.parse("2026-06-18T08:00:00Z").toString());
    executionCollectorRepository.save(row);
  }

  private JsonNode toJsonNode(String rawJson) {
    try {
      return JSON_MAPPER.readTree(rawJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid test alerts JSON: " + rawJson, e);
    }
  }

  private AttackPathExecutionDetailDTO executionDetailForCurrentTenant(String executionId) {
    TenantContext.setCurrentTenant(tenant.getId());
    try {
      return graphService.executionDetail(SIM, executionId);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  @Test
  @DisplayName("an asset-scoped detection expectation surfaces its platform and alerts")
  void assetExecution_listsDetectionPlatform_withAlerts() {
    // Arrange
    Inject toPersist = InjectFixture.getDefaultInject();
    Inject inject = injectRepository.save(toPersist);
    String executionId = seedExecution(inject.getId(), null);
    saveCollectorRow(
        executionId,
        "DETECTION",
        "Detected",
        "[{\"id\":\"a\",\"title\":\"Alert A\",\"date\":\"2026-06-18T08:00:00Z\",\"link\":\"http://siem/a\"},{\"id\":\"b\",\"title\":\"Alert B\",\"date\":\"2026-06-18T08:00:01Z\",\"link\":\"http://siem/b\"}]",
        1.0);
    entityManager.flush();

    // Act
    AttackPathExecutionDetailDTO d = executionDetailForCurrentTenant(executionId);

    // Assert
    assertThat(d).isNotNull();
    assertThat(d.securityPlatforms()).hasSize(1);
    AttackPathSecurityPlatformDTO p = d.securityPlatforms().getFirst();
    assertThat(p.bucket()).isEqualTo("DETECTION");
    assertThat(p.platformType()).isEqualTo("SIEM");
    assertThat(p.platformName()).isEqualTo("Splunk");
    assertThat(p.status()).isEqualTo("SUCCESS");
    assertThat(p.alerts())
        .extracting(io.openaev.service.attackpath.dto.AttackPathAlertDTO::title)
        .containsExactlyInAnyOrder("Alert A", "Alert B");
  }

  @Test
  @DisplayName("a platform that prevented is also surfaced as having detected")
  void preventedPlatform_alsoAppearsAsDetected() {
    // Arrange
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    String executionId = seedExecution(inject.getId(), null);
    saveCollectorRow(executionId, "PREVENTION", "Prevented", "[]", 1.0);
    entityManager.flush();

    // Act
    AttackPathExecutionDetailDTO d = executionDetailForCurrentTenant(executionId);

    // Assert
    assertThat(d.securityPlatforms())
        .extracting(AttackPathSecurityPlatformDTO::bucket)
        .containsExactlyInAnyOrder("PREVENTION", "DETECTION");
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
    // Arrange
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    Agent agent = agentRepository.save(AgentFixture.createAgent(asset, "ext-agent"));
    String executionId = seedExecution(inject.getId(), agent.getId());
    saveCollectorRow(executionId, "DETECTION", "Detected", "[]", 1.0);
    entityManager.flush();

    // Act
    AttackPathExecutionDetailDTO d = executionDetailForCurrentTenant(executionId);

    // Assert
    assertThat(d.securityPlatforms()).hasSize(1);
    assertThat(d.securityPlatforms().getFirst().bucket()).isEqualTo("DETECTION");
    assertThat(d.securityPlatforms().getFirst().platformName()).isEqualTo("Splunk");
  }

  @Test
  @DisplayName(
      "prevented ⇒ detected overrides a contradictory 'not detected' for the same platform")
  void preventionSuccess_upgradesContradictoryDetectionToSuccess() {
    // Arrange
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    String executionId = seedExecution(inject.getId(), null);
    saveCollectorRow(executionId, "PREVENTION", "Prevented", "[]", 1.0);
    saveCollectorRow(executionId, "DETECTION", "Not Detected", "[]", 0.0);
    entityManager.flush();

    // Act
    AttackPathExecutionDetailDTO d = executionDetailForCurrentTenant(executionId);

    // Assert
    AttackPathSecurityPlatformDTO detected =
        d.securityPlatforms().stream()
            .filter(p -> "DETECTION".equalsIgnoreCase(p.bucket()))
            .findFirst()
            .orElseThrow();
    assertThat(detected.status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("an inactive Enterprise licence yields no security platforms (no error)")
  void inactiveLicence_yieldsEmpty() {
    // Arrange
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(false);
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());
    String executionId = seedExecution(inject.getId(), null);
    saveCollectorRow(executionId, "DETECTION", "Detected", "[]", 1.0);
    entityManager.flush();

    // Act + Assert
    assertThat(executionDetailForCurrentTenant(executionId).securityPlatforms()).isEmpty();
  }

  @Test
  @DisplayName("a target with neither agent nor asset resolves to no platforms")
  void noScope_yieldsEmpty() {
    // Arrange
    Inject inject = injectRepository.save(InjectFixture.getDefaultInject());

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

    // Act + Assert
    assertThat(executionDetailForCurrentTenant(executionId).securityPlatforms()).isEmpty();
  }
}
