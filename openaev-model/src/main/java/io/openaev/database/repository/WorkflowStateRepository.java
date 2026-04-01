package io.openaev.database.repository;

import io.openaev.database.model.WorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowStateRepository extends JpaRepository<WorkflowState, String> {

  WorkflowState findByStepTemplate_IdAndWorkflowExecution_Id(
      String stepTemplateId, String workflowExecutionId);

  WorkflowState findByStepTemplateIsNullAndWorkflowExecutionId(String id);
}
