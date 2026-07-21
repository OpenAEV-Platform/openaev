package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
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
        .containsExactly(new ConsumedFindingKeyDTO("port", "EQ", "445"));
  }
}
