package io.openaev.database.repository;

import io.openaev.database.model.STEP_STATUS;
import io.openaev.database.model.Step;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StepRepository extends JpaRepository<Step, String> {
  // STEP TEMPLATE
  List<Step> findAllByStepTemplateIdIsNullAndWorkflowId(String workflowId);

  Step findByStepTemplateIdIsNullAndIdAndStatus(String stepId, STEP_STATUS status);

  Step findByStepTemplateIdIsNotNullAndIdAndStatus(String stepId, STEP_STATUS status);

  // STEP EXECUTED
  List<Step> findAllByStepTemplateIdAndWorkflowId(String stepTemplateId, String workflowId);
}
