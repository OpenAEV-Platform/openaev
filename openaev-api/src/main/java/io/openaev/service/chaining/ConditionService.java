package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.repository.ConditionRepository;
import org.springframework.stereotype.Service;

@Service
public class ConditionService {
  ConditionRepository conditionRepository;

  public Condition saveCondition(Condition condition) {
    return conditionRepository.save(condition);
  }
}
