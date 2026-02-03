package io.openaev.database.model;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.composers.ConditionComposer;
import java.util.List;
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

  @Test
  void testFindAllByStepId() {
    Step step = StepFixture.getDefaultStepTemplate();

    Condition condition1 = Condition.builder().key("key1").value("val1").build();
    Condition condition2 = Condition.builder().key("key2").value("val2").build();

    conditionComposer.forCondition(condition1).withStep(step).persist();
    conditionComposer.forCondition(condition2).withStep(step).persist();

    List<Condition> conditions = conditionRepository.findAllByStep_Id(step.getId());

    Assertions.assertEquals(2, conditions.size());
    Assertions.assertTrue(conditions.stream().anyMatch(c -> c.getKey().equals("key1")));
    Assertions.assertTrue(conditions.stream().anyMatch(c -> c.getKey().equals("key2")));
  }
}
