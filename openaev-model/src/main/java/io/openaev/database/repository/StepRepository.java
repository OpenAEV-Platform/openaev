package io.openaev.database.repository;

import io.openaev.database.model.STEP_STATUS;
import io.openaev.database.model.Step;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StepRepository extends JpaRepository<Step, String> {
  // STEP TEMPLATE
  List<Step> findAllByStepTemplateIdIsNullAndWorkflowId(String workflowId);

  Step findByStepTemplateIdIsNullAndIdAndStatus(String stepId, STEP_STATUS status);

  Step findByStepTemplateIdIsNotNullAndIdAndStatus(String stepId, STEP_STATUS status);

  @Query(
      value =
          "SELECT count(*) FROM steps where step_workflow_id=:idWorkflowRun and step_status != 'END'",
      nativeQuery = true)
  int countRunningStep(@Param("idWorkflowRun") String idWorkflowRun);

  List<Step> findAllByStatus(STEP_STATUS status);

  List<Step> findAllByStatusAndStepTemplateIdAndWorkflowId(
      STEP_STATUS status, String stepTemplateId, String idWorkflowRun);

  // STEP EXECUTED
  List<Step> findAllByStepTemplateIdAndWorkflowId(String stepTemplateId, String idWorkflowRun);
}
