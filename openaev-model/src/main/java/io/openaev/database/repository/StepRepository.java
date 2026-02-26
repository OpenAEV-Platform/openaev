package io.openaev.database.repository;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StepRepository extends JpaRepository<Step, String> {

  // STEP TEMPLATE

  /**
   * Retrieves all {@link Step} entities in a workflow that are step template.
   *
   * @param workflowId the ID of the workflow to filter steps by
   * @return a list of steps template in the specified workflow
   */
  List<Step> findAllByStepTemplateIdIsNullAndWorkflowId(String workflowId);

  /**
   * Retrieves a {@link Step} entity by its ID and status, ensuring it is not based on a step
   * template.
   *
   * @param stepId the ID of the step
   * @param status the status of the step
   * @return the matching step, or null if not found
   */
  Optional<Step> findByStepTemplateIdIsNullAndIdAndStatus(String stepId, StepStatus status);

  /**
   * Retrieves a {@link Step} entity by its ID and status, ensuring it is based on a step template.
   *
   * @param stepId the ID of the step
   * @param status the status of the step
   * @return the matching step, or null if not found
   */
  Step findByStepTemplateIdIsNotNullAndIdAndStatus(String stepId, StepStatus status);

  Optional<Step> findByIdAndStatus(String stepId, StepStatus status);

  /**
   * Counts the number of running steps in a workflow run (steps not in 'END' status).
   *
   * @param idWorkflowRun the ID of the workflow run
   * @return the count of running steps
   */
  @Query(
      value =
          "SELECT count(*) FROM steps WHERE step_workflow_id=:idWorkflowRun AND step_status != 'END'",
      nativeQuery = true)
  int countRunningStep(@Param("idWorkflowRun") String idWorkflowRun);

  /**
   * Counts the number of steps executed for a given step template in a workflow run.
   *
   * @param idWorkflowRun the ID of the workflow run
   * @param stepTemplateId the ID of the step template
   * @return the count of executed steps
   */
  @Query(
      value =
          "SELECT count(*) FROM steps WHERE step_workflow_id=:idWorkflowRun AND step_template_id=:stepTemplateId",
      nativeQuery = true)
  int countStepExecutedByStepTemplateIdAndWorkflowRunId(
      @Param("idWorkflowRun") String idWorkflowRun, @Param("stepTemplateId") String stepTemplateId);

  // STEP EXECUTED

  /**
   * Retrieves all executed {@link Step} entities for a given step template and workflow run.
   *
   * @param stepTemplateId the ID of the step template
   * @param idWorkflowRun the ID of the workflow run
   * @return a list of executed steps matching the criteria
   */
  @Query(
      value =
          "SELECT * FROM steps WHERE step_workflow_id=:idWorkflowRun AND step_template_id=:stepTemplateId",
      nativeQuery = true)
  List<Step> findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
      @Param("stepTemplateId") String stepTemplateId, @Param("idWorkflowRun") String idWorkflowRun);

  /**
   * Retrieves all {@link Step} entities for a given step template and workflow.
   *
   * @param stepTemplateId the ID of the step template
   * @param idWorkflowRun the ID of the workflow
   * @return a list of steps matching the criteria
   */
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

  @Query(
      value =
          """
        SELECT DISTINCT s.step_id
        FROM steps s
        WHERE EXISTS (
          SELECT 1
          FROM injects_expectations ie
          WHERE ie.inject_expectation_id IN (:expectationIds)
          AND jsonb_path_exists(
            s.step_data,
            '$.** ? (@.inject_id == $id)',
            jsonb_build_object('id', to_jsonb(ie.inject_id))
          )
        )
        """,
      nativeQuery = true)
  Set<String> findStepIdsByExpectationIds(@Param("expectationIds") Set<String> expectationIds);
}
