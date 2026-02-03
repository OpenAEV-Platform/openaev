package io.openaev.utils.fixtures;

import io.openaev.database.model.WORKFLOW_STATUS;
import io.openaev.database.model.Workflow;
import java.time.Instant;
import java.util.ArrayList;

public class WorkflowFixture {

  public static Workflow getDefaultWorkflowTemplate() {
    Workflow workflow = new Workflow();
    workflow.setStatus(WORKFLOW_STATUS.TEMPLATE);
    workflow.setVersion(1);
    workflow.setEdited(false);
    workflow.setWorkflowCreatedAt(Instant.now());
    workflow.setWorkflowUpdatedAt(Instant.now());
    workflow.setWorkflowTemplate(null);
    workflow.setWorkflowsExecuted(new ArrayList<>());
    workflow.setSteps(new ArrayList<>());
    return workflow;
  }

  public static Workflow getDefaultWorkflowExecution(WORKFLOW_STATUS status) {
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
