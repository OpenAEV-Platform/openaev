package io.openaev.database.model;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.utils.fixtures.ConditionFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.composers.ConditionComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
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

  @Test
  void whenFindAllByStepId_thenReturnsAllConditionsForThatStep() {
    // GIVEN: one persisted step
    StepComposer.Composer stepOriginComposer =
        stepComposer.forStep(StepFixture.getDefaultStepTemplate());

    // AND: two conditions linked to the SAME step
    Condition condition1 =
        conditionComposer
            .forCondition(ConditionFixture.getDefaultCondition("key1", "val1"))
            .withStep(stepOriginComposer)
            .persist()
            .get();
    Condition condition2 =
        conditionComposer
            .forCondition(ConditionFixture.getDefaultCondition("key2", "val2"))
            .withStep(stepOriginComposer)
            .persist()
            .get();

    // WHEN
    List<Condition> conditions = conditionRepository.findAllByStep_Id(condition1.getStep().getId());

    // THEN
    Assertions.assertEquals(2, conditions.size());

    Set<String> keys = conditions.stream().map(Condition::getKey).collect(Collectors.toSet());

    Assertions.assertEquals(Set.of(condition1.getKey(), condition2.getKey()), keys);
  }
}
