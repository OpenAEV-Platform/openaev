package io.openaev.database.repository;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepState;
import io.openaev.database.model.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StepStateRepository extends JpaRepository<StepState, String> {
  StepState findByStepTemplate_IdAndWorkflowExecution_Id(
      String stepTemplateId, String workflowExecutionId);

  StepState findByStepTemplateAndWorkflowExecution(
      Step stepTemplateId, Workflow workflowExecutionId);
}
