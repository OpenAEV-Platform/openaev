package io.openaev.api.chaining;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.database.model.Step;

/** Mapper for Step template API DTOs. */
public final class StepMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private StepMapper() {}
  
  public static StepOutput toOutput(Step step) {
    try {
      return StepOutput.builder()
          .id(step.getId())
          .status(step.getStatus())
          .conditionKeyTypes(step.getConditionKeyTypes())
          .data(
              step.getData() == null
                  ? null
                  : OBJECT_MAPPER.readValue(step.getData(), DataOutputStep.class))
          .createdAt(step.getCreatedAt())
          .updatedAt(step.getUpdatedAt())
          .build();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to deserialize step data", e);
    }
  }
}
