package io.openaev.database.repository;

import io.openaev.database.model.STEP_STATUS;
import io.openaev.database.model.Step;
import java.util.List;
import java.util.Optional;
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
          "SELECT count(*) FROM steps WHERE step_workflow_id=:idWorkflowRun AND step_status != 'END'",
      nativeQuery = true)
  int countRunningStep(@Param("idWorkflowRun") String idWorkflowRun);

  @Query(
      value =
          "SELECT count(*) FROM steps WHERE step_workflow_id=:idWorkflowRun AND step_template_id=:stepTemplateId",
      nativeQuery = true)
  int countStepExecutedByStepTemplateIdAndWorkflowRunId(
      @Param("idWorkflowRun") String idWorkflowRun, @Param("stepTemplateId") String stepTemplateId);

  List<Step> findAllByStatus(STEP_STATUS status);

  // STEP EXECUTED

  @Query(
      value =
          "SELECT * FROM steps WHERE step_workflow_id=:idWorkflowRun AND step_template_id=:stepTemplateId",
      nativeQuery = true)
  List<Step> findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
      @Param("stepTemplateId") String stepTemplateId, @Param("idWorkflowRun") String idWorkflowRun);

  List<Step> findAllByStepTemplateIdAndWorkflowId(String stepTemplateId, String idWorkflowRun);

  /**
   * Return the stepId associated to a given injectId if it exists
   *
   * @param injectId the injectId for which we want the associated step
   * @return An optional filled with the stepId if found
   */
  @Query(
      value =
          """
      SELECT step_id
      FROM steps
      WHERE jsonb_path_exists(
        step_data,
        '$.** ? (@.inject_id == $id)',
        jsonb_build_object('id', to_jsonb(:injectId))
      )
      LIMIT 1
      """,
      nativeQuery = true)
  Optional<String> findStepIdByInjectId(@Param("injectId") String injectId);
}
