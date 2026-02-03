package io.openaev.utils.fixtures;

import io.openaev.database.model.WORKFLOW_STATUS;
import io.openaev.database.model.Workflow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

public class WorkflowFixture {

  public static Workflow getDefaultWorkflowTemplate() {
    return Workflow.builder()
        .id(UUID.randomUUID().toString())
        .status(WORKFLOW_STATUS.TEMPLATE)
        .version(1)
        .isEdited(false)
        .workflowCreatedAt(Instant.now())
        .workflowUpdatedAt(Instant.now())
        .workflowTemplate(null)
        .workflowsExecuted(new ArrayList<>())
        .steps(new ArrayList<>())
        .build();
  }

  public static Workflow getDefaultWorkflowExecution(WORKFLOW_STATUS status) {
    return Workflow.builder()
        .id(UUID.randomUUID().toString())
        .status(status)
        .version(1)
        .isEdited(false)
        .workflowCreatedAt(Instant.now())
        .workflowUpdatedAt(Instant.now())
        .steps(new ArrayList<>())
        .build();
  }
}
