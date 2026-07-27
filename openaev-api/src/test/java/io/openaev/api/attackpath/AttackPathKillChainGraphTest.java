package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import io.openaev.utils.fixtures.ConditionFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ConditionComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full mode carries the kill-chain fields on the execution feed node, resolved from the step
 * template's conditions and keyed by {@code stepTemplateId}. Real stack, no mocks.
 */
@Transactional
@WithMockUser(isAdmin = true)
class AttackPathKillChainGraphTest extends IntegrationTest {

  private static final String SIM = "SIM-KILLCHAIN";

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private ConditionComposer conditionComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  @Test
  @DisplayName("the execution feed node carries the resolved kill-chain fields in full mode")
  void executionNodeCarriesKillChain() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-killchain-tenant"));

    // a step template with a DEPEND_ON and a consumed EQ key
    StepComposer.Composer step = stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(step)
        .persist();
    Condition dependOn = new Condition();
    dependOn.setType(ConditionType.DEPEND_ON);
    dependOn.setValue("prereq-step-tpl");
    conditionComposer.forCondition(dependOn).withStep(step).persist();
    conditionComposer
        .forCondition(ConditionFixture.getDefaultCondition(PrimitiveType.Port, "445"))
        .withStep(step)
        .persist();

    // an attack-path execution whose step template id points at that step
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector("nmap");
    e.setTargetKind("ASSET");
    e.setTargetAssetId("host-x");
    e.setTargetKey("host-x");
    e.setTargetHostname("host-x");
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    e.setStepTemplateId(step.get().getId());
    executionRepository.save(e);
    entityManager.flush();

    AttackPathDTO full = graphService.buildGraph(SIM, "full");

    AttackPathNodeDTO node =
        full.attackPathExecutions().stream()
            .filter(n -> step.get().getId().equals(n.getStepTemplateId()))
            .findFirst()
            .orElseThrow();
    assertThat(node.getDependsOn()).containsExactly("prereq-step-tpl");
    assertThat(node.getConsumedFindingKeys())
        .containsExactly(new ConsumedFindingKeyDTO("port", "EQ", "445", null));
  }

  @Test
  @DisplayName("a step that consumes an event gets its finding's producer added to dependsOn")
  void consumerGetsProducerInDependsOn() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-eventdep-tenant"));

    // Consumer step B: a filter condition on share_name IS_NOT_NULL (the SMB "share found" event).
    StepComposer.Composer stepB = stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepB)
        .persist();
    Condition consumed = ConditionFixture.getDefaultCondition(PrimitiveType.ShareName, null);
    consumed.setType(ConditionType.IS_NOT_NULL);
    conditionComposer.forCondition(consumed).withStep(stepB).persist();

    // The producer ran earlier and produced a share (presented as file); the consumer ran later.
    AttackPathExecution producer =
        saveExecution(tenant, "nmap", "producer-step-tpl", Instant.parse("2026-06-18T08:00:00Z"));
    saveFinding(tenant, "share", "\\\\host\\NETLOGON", producer.getId());
    saveExecution(tenant, "netexec", stepB.get().getId(), Instant.parse("2026-06-18T08:05:00Z"));
    entityManager.flush();

    AttackPathNodeDTO consumerNode =
        graphService.buildGraph(SIM, "full").attackPathExecutions().stream()
            .filter(n -> stepB.get().getId().equals(n.getStepTemplateId()))
            .findFirst()
            .orElseThrow();
    assertThat(consumerNode.getDependsOn()).contains("producer-step-tpl");
  }

  @Test
  @DisplayName("explicit DEPEND_ON is preserved and every matching producer is merged in")
  void mergesExplicitDependOnAndMultipleProducers() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-eventdep-merge"));
    StepComposer.Composer stepB =
        consumerStep(PrimitiveType.ShareName, ConditionType.IS_NOT_NULL, null);
    Condition dependOn = new Condition();
    dependOn.setType(ConditionType.DEPEND_ON);
    dependOn.setValue("explicit-prereq");
    conditionComposer.forCondition(dependOn).withStep(stepB).persist();

    // Two earlier producers of a share (presented file), each on its own step.
    AttackPathExecution p1 =
        saveExecution(tenant, "nmap", "prod-1", Instant.parse("2026-06-18T08:00:00Z"));
    saveFinding(tenant, "share", "\\\\host\\NETLOGON", p1.getId());
    AttackPathExecution p2 =
        saveExecution(tenant, "smb", "prod-2", Instant.parse("2026-06-18T08:01:00Z"));
    saveFinding(tenant, "share", "\\\\host\\SYSVOL", p2.getId());
    saveExecution(tenant, "netexec", stepB.get().getId(), Instant.parse("2026-06-18T08:05:00Z"));
    entityManager.flush();

    assertThat(nodeForStep(stepB.get().getId()).getDependsOn())
        .containsExactlyInAnyOrder("explicit-prereq", "prod-1", "prod-2");
  }

  @Test
  @DisplayName("a step that produces and consumes the same event does not depend on itself")
  void guardsSelfDependency() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-eventdep-self"));
    StepComposer.Composer selfStep =
        consumerStep(PrimitiveType.ShareName, ConditionType.IS_NOT_NULL, null);
    AttackPathExecution selfExec =
        saveExecution(
            tenant, "netexec", selfStep.get().getId(), Instant.parse("2026-06-18T08:00:00Z"));
    saveFinding(tenant, "share", "\\\\host\\NETLOGON", selfExec.getId());
    entityManager.flush();

    assertThat(nodeForStep(selfStep.get().getId()).getDependsOn()).isNullOrEmpty();
  }

  @Test
  @DisplayName("a producer that ran after the consumer is excluded (causality)")
  void guardsCausality() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-eventdep-causality"));
    StepComposer.Composer stepB =
        consumerStep(PrimitiveType.ShareName, ConditionType.IS_NOT_NULL, null);
    saveExecution(tenant, "netexec", stepB.get().getId(), Instant.parse("2026-06-18T08:00:00Z"));
    AttackPathExecution later =
        saveExecution(tenant, "nmap", "later-prod", Instant.parse("2026-06-18T09:00:00Z"));
    saveFinding(tenant, "share", "\\\\host\\NETLOGON", later.getId());
    entityManager.flush();

    assertThat(nodeForStep(stepB.get().getId()).getDependsOn()).isNullOrEmpty();
  }

  @Test
  @DisplayName("EQ matches the exact producer value; IN matches a member; a non-match adds nothing")
  void operatorCoverageEqAndIn() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-eventdep-ops"));
    AttackPathExecution hit =
        saveExecution(tenant, "nmap", "port-445", Instant.parse("2026-06-18T08:00:00Z"));
    saveFinding(tenant, "port", "445", hit.getId());
    AttackPathExecution miss =
        saveExecution(tenant, "nmap", "port-443", Instant.parse("2026-06-18T08:01:00Z"));
    saveFinding(tenant, "port", "443", miss.getId());

    StepComposer.Composer eqStep = consumerStep(PrimitiveType.Port, ConditionType.EQ, "445");
    saveExecution(tenant, "netexec", eqStep.get().getId(), Instant.parse("2026-06-18T08:05:00Z"));
    StepComposer.Composer inStep =
        consumerStep(PrimitiveType.Port, ConditionType.IN, "139,445,3389");
    saveExecution(
        tenant, "crackmapexec", inStep.get().getId(), Instant.parse("2026-06-18T08:06:00Z"));
    entityManager.flush();

    // EQ 445 → only the 445 producer, not 443.
    assertThat(nodeForStep(eqStep.get().getId()).getDependsOn()).containsExactly("port-445");
    // IN {139,445,3389} → the 445 producer matches.
    assertThat(nodeForStep(inStep.get().getId()).getDependsOn()).contains("port-445");
  }

  private AttackPathExecution saveExecution(
      Tenant tenant, String injector, String stepTemplateId, Instant executedAt) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector(injector);
    e.setTargetKind("ASSET");
    e.setTargetAssetId("host-x");
    e.setTargetKey("host-x");
    e.setTargetHostname("host-x");
    e.setExecutedAt(executedAt);
    e.setStepTemplateId(stepTemplateId);
    return executionRepository.save(e);
  }

  private void saveFinding(Tenant tenant, String type, String value, String executionId) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type); // a "share" is presented as the native "file" type at the read boundary
    f.setValue(value);
    f.setEndpointId("host-x");
    f.setEndpointKey("host-x");
    String findingId = findingRepository.save(f).getId();
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(executionId);
    link.setFindingId(findingId);
    entityManager.persist(link);
  }

  /**
   * A persisted consumer step template whose filter consumes {@code keyType} with {@code operator}.
   */
  private StepComposer.Composer consumerStep(
      PrimitiveType keyType, ConditionType operator, String value) {
    StepComposer.Composer step = stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(step)
        .persist();
    Condition consumed = ConditionFixture.getDefaultCondition(keyType, value);
    consumed.setType(operator);
    conditionComposer.forCondition(consumed).withStep(step).persist();
    return step;
  }

  private AttackPathNodeDTO nodeForStep(String stepTemplateId) {
    return graphService.buildGraph(SIM, "full").attackPathExecutions().stream()
        .filter(n -> stepTemplateId.equals(n.getStepTemplateId()))
        .findFirst()
        .orElseThrow();
  }
}
