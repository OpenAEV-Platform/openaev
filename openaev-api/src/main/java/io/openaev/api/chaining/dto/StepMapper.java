package io.openaev.api.chaining.dto;

import io.openaev.database.model.Step;

/** Mapper for Step template API DTOs. */
public final class StepMapper {

  private StepMapper() {}

  public static StepInput toCreateInput(StepInput input) {
    return input;
  }

  public static StepOutput toOutput(Step step) {
    return StepOutput.builder()
        .id(step.getId())
        .limitExecution(step.getLimitExecution())
        .status(step.getStatus() != null ? step.getStatus().name() : null)
        .data(step.getData())
        .createdAt(step.getCreatedAt())
        .updatedAt(step.getUpdatedAt())
        .build();
  }
}
