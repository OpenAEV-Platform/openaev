package io.openaev.api.chaining;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.database.model.ConditionStep;
import io.openaev.database.model.Step;
import java.util.List;

/** Mapper for Step template API DTOs. */
public final class StepMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private StepMapper() {}

  public static StepOutput toOutput(Step step) {
    try {
      List<String> rootConditionIds =
          step.getConditionSteps().stream()
              .filter(ConditionStep::isRoot)
              .map(cs -> cs.getCondition().getId())
              .toList();
      return StepOutput.builder()
          .id(step.getId())
          .status(step.getStatus())
          .conditionIds(rootConditionIds)
          .conditionKeyTypes(step.getConditionKeyTypes())
          .data(step.getData() == null ? null : OBJECT_MAPPER.readTree(step.getData()))
          .createdAt(step.getCreatedAt())
          .updatedAt(step.getUpdatedAt())
          .build();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to parse step data as JSON", e);
    }
  }
}
