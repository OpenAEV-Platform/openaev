package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.repository.ConditionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ConditionService {
  private final ConditionRepository conditionRepository;

  public Condition saveCondition(Condition condition) {
    return conditionRepository.save(condition);
  }
}
