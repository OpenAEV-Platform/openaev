package io.openaev.utils.fixtures;

import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import java.time.Instant;
import java.util.ArrayList;

public class WorkflowFixture {

  public static Workflow getDefaultWorkflowTemplate() {
    Workflow workflow = new Workflow();
    workflow.setStatus(WorkflowStatus.TEMPLATE);
    workflow.setVersion(1);
    workflow.setEdited(false);
    workflow.setWorkflowCreatedAt(Instant.now());
    workflow.setWorkflowUpdatedAt(Instant.now());
    workflow.setWorkflowTemplate(null);
    workflow.setWorkflowsExecuted(new ArrayList<>());
    workflow.setSteps(new ArrayList<>());
    return workflow;
  }

  public static Workflow getDefaultWorkflowExecution(WorkflowStatus status) {
    Workflow workflow = new Workflow();
    workflow.setStatus(status);
    workflow.setVersion(1);
    workflow.setEdited(false);
    workflow.setWorkflowCreatedAt(Instant.now());
    workflow.setWorkflowUpdatedAt(Instant.now());
    workflow.setSteps(new ArrayList<>());
    return workflow;
  }
}
