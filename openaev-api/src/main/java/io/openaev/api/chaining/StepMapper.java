package io.openaev.api.chaining;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.MapperConditionOutput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.database.model.ConditionStep;
import io.openaev.database.model.ConditionType;
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
              .filter(cs -> cs.getCondition().getType() != ConditionType.MAPPER)
              .map(cs -> cs.getCondition().getId())
              .toList();

      // Extract mapper conditions linked to this step
      List<MapperConditionOutput> mapperConditions =
          step.getConditionSteps().stream()
              .map(ConditionStep::getCondition)
              .filter(c -> c.getType() == ConditionType.MAPPER)
              .map(
                  c ->
                      MapperConditionOutput.builder()
                          .conditionKeyType(c.getKeyType())
                          .conditionKey(c.getKey())
                          .conditionValue(c.getValue())
                          .conditionMappingType(c.getMappingType())
                          .build())
              .toList();

      return StepOutput.builder()
          .id(step.getId())
          .status(step.getStatus())
          .conditionIds(rootConditionIds)
          .mapperConditions(mapperConditions)
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
