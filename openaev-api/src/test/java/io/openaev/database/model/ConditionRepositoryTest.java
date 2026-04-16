package io.openaev.database.model;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.ConditionRepository;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ConditionRepositoryTest extends IntegrationTest {

  @Autowired private ConditionRepository conditionRepository;
  @Autowired private ConditionComposer conditionComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  @Test
  void whenFindAllByStepId_thenReturnsAllConditionsForThatStep() {
    // GIVEN: one persisted step
    StepComposer.Composer stepComposer1 =
        stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer1)
        .persist();

    // AND: two conditions linked to the SAME step
    Condition condition1 =
        conditionComposer
            .forCondition(ConditionFixture.getDefaultCondition(ConditionKeyType.IPV4, "val1"))
            .withStep(stepComposer1)
            .persist()
            .get();
    Condition condition2 =
        conditionComposer
            .forCondition(ConditionFixture.getDefaultCondition(ConditionKeyType.IPV4, "val2"))
            .withStep(stepComposer1)
            .persist()
            .get();

    // Ensure persisted links are flushed before querying by step ID.
    conditionRepository.flush();

    // WHEN
    List<Condition> conditions =
        conditionRepository.findAllLinkedToStepId(stepComposer1.get().getId());

    // THEN
    Assertions.assertEquals(2, conditions.size());

    Set<ConditionKeyType> keys =
        conditions.stream().map(Condition::getKeyType).collect(Collectors.toSet());
    Assertions.assertEquals(Set.of(ConditionKeyType.IPV4), keys);

    Set<String> values = conditions.stream().map(Condition::getValue).collect(Collectors.toSet());
    Assertions.assertEquals(Set.of(condition1.getValue(), condition2.getValue()), values);
  }
}
