package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepConditionRow;
import io.openaev.service.attackpath.AttackPathKillChainResolver.KillChainMeta;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import io.openaev.utils.fixtures.ConditionFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ConditionComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The kill-chain resolution over the real persistence: the batched read returns the conditions
 * linked to the step (the tree roots), and the resolver flattens composite filters to surface every
 * leaf key. Real stack, real conditions, no mocks.
 */
@SpringBootTest
@Transactional
class AttackPathKillChainResolutionIntegrationTest extends IntegrationTest {

  @Autowired private ConditionRepository conditionRepository;
  @Autowired private AttackPathKillChainResolver resolver;
  @Autowired private ConditionComposer conditionComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  @Test
  @DisplayName("batched read returns roots only; the resolver flattens composite filters")
  void resolvesDependsOnAndConsumedKeysThroughTheRealStack() {
    // GIVEN one persisted step template
    StepComposer.Composer step = stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(step)
        .persist();

    // AND a DEPEND_ON condition (prerequisite step template id in its value)
    Condition dependOn = new Condition();
    dependOn.setType(ConditionType.DEPEND_ON);
    dependOn.setValue("prereq-step-tpl");
    conditionComposer.forCondition(dependOn).withStep(step).persist();

    // AND a single-leaf EQ filter
    conditionComposer
        .forCondition(ConditionFixture.getDefaultCondition(PrimitiveType.Port, "445"))
        .withStep(step)
        .persist();

    // AND a composite AND filter whose two leaf keys live in its children, NOT linked to the step
    Condition andRoot = new Condition();
    andRoot.setType(ConditionType.AND);
    ConditionComposer.Composer and =
        conditionComposer.forCondition(andRoot).withStep(step).persist();
    conditionComposer
        .forCondition(ConditionFixture.getDefaultCondition(PrimitiveType.Port, "139"))
        .withParentCondition(and)
        .persist();
    conditionComposer
        .forCondition(ConditionFixture.getDefaultCondition(PrimitiveType.Service, "smb"))
        .withParentCondition(and)
        .persist();

    conditionRepository.flush();

    // WHEN the batched read runs for the step
    String stepId = step.get().getId();
    List<StepConditionRow> rows = conditionRepository.findAllLinkedToStepIdIn(Set.of(stepId));

    // THEN only the three ROOTS are returned (the AND's children are not linked to the step)
    assertThat(rows).hasSize(3);
    assertThat(rows).allMatch(r -> r.stepTemplateId().equals(stepId));

    // AND the resolver flattens the composite filter, surfacing every leaf key
    KillChainMeta meta = resolver.resolve(rows.stream().map(StepConditionRow::condition).toList());

    assertThat(meta.dependsOn()).containsExactly("prereq-step-tpl");
    assertThat(meta.consumedFindingKeys())
        .containsExactlyInAnyOrder(
            new ConsumedFindingKeyDTO("port", "EQ", "445", null),
            new ConsumedFindingKeyDTO("port", "EQ", "139", null),
            new ConsumedFindingKeyDTO("service", "EQ", "smb", null));
  }
}
