package io.openaev.utils.fixtures;

import io.openaev.database.model.STEP_ACTION_CLASS;
import io.openaev.database.model.STEP_STATUS;
import io.openaev.database.model.Step;
import java.time.Instant;
import java.util.UUID;

public class StepFixture {

  public static Step getDefaultStepTemplate() {
    return Step.builder()
        .id(UUID.randomUUID().toString())
        .stepAction(STEP_ACTION_CLASS.INJECT_EXECUTION)
        .output("{}")
        .output_parser("{}")
        .input("{}")
        .data("{}")
        .limitExecution(1)
        .conditionExecuted("true")
        .status(STEP_STATUS.TEMPLATE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  public static Step getDefaultStepExecution(STEP_STATUS status) {
    return Step.builder()
        .id(UUID.randomUUID().toString())
        .stepAction(STEP_ACTION_CLASS.INJECT_EXECUTION)
        .output("{}")
        .output_parser("{}")
        .input("{}")
        .data("{}")
        .limitExecution(1)
        .conditionExecuted("true")
        .status(status)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }
}
