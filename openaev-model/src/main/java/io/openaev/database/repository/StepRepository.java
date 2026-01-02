package io.openaev.database.repository;

import io.openaev.database.model.STEP_STATUS;
import io.openaev.database.model.Step;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

  @Query(
      value =
          "SELECT count(*) FROM steps where step_workflow_id=:idWorkflowRun and step_template_id=:stepTemplateId",
      nativeQuery = true)
  int countStepExecutedByStepTemplateIdAndWorkflowRunId(
      @Param("idWorkflowRun") String idWorkflowRun, @Param("stepTemplateId") String stepTemplateId);

  List<Step> findAllByStatus(STEP_STATUS status);

  @Query(
      value =
          "SELECT * FROM steps where step_workflow_id=:idWorkflowRun and step_template_id=:stepTemplateId",
      nativeQuery = true)
  List<Step> findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
      String stepTemplateId, String idWorkflowRun);

  // STEP EXECUTED
  List<Step> findAllByStepTemplateIdAndWorkflowId(String stepTemplateId, String idWorkflowRun);
}
