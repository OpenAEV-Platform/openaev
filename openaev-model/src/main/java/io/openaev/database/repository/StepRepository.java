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
   * Retrieves all {@link Step} entities that are step templates (filtered at the database level
   * instead of loading the whole table).
   *
   * @return a list of all step templates
   */
  List<Step> findAllByStepTemplateIdIsNull();

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
   * Counts the number of active steps in a workflow run.
   *
   * @param idWorkflowRun the ID of the workflow run
   * @return the count of active steps
   */
  @Query(
      value =
          "SELECT count(*) FROM Step WHERE workflow.id=:idWorkflowRun AND status in :activeStatus")
  int countActiveSteps(
      @Param("idWorkflowRun") String idWorkflowRun,
      @Param("activeStatus") List<StepStatus> activeStatus);

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

  /**
   * Resolves the tenant that owns a step, as a projection: step -&gt; workflow -&gt; simulation
   * -&gt; tenant. Used to stamp the tenant on chaining events (#6357). A single query with no lazy
   * association access, so it is safe on any thread with no open session (the update-event producer
   * runs on scheduler/queue threads where open-in-view is inactive). Empty for a workflow with no
   * simulation (standalone run); the caller falls back to the default tenant.
   */
  @Query("SELECT s.workflow.simulation.tenant.id FROM Step s WHERE s.id = :stepId")
  Optional<String> findTenantIdByStepId(@Param("stepId") String stepId);

  /**
   * Return the injectId frozen in a step's data, if present. The engine writes it into {@code
   * step_data} at run ({@code InjectExecutionStep.setInjectId}), so the attack-path read can
   * resolve the "Action details" inject link from the durable step rather than storing it on the
   * frozen row.
   *
   * @param stepId the step whose data may carry an inject_id
   * @return an optional filled with the injectId if found
   */
  @Query(
      value =
          """
      SELECT jsonb_path_query_first(step_data, '$.**.inject_id') #>> '{}'
      FROM steps
      WHERE step_id = :stepId
      """,
      nativeQuery = true)
  Optional<String> findInjectIdByStepId(@Param("stepId") String stepId);

  /**
   * Returns the step IDs associated with any of the given inject IDs in a single query.
   *
   * @param injectIds the inject IDs for which we want the associated steps
   * @return set of step IDs that reference any of the given inject IDs
   */
  @Query(
      value =
          """
      SELECT DISTINCT s.step_id
      FROM steps s
      WHERE EXISTS (
        SELECT 1
        FROM injects i
        WHERE i.inject_id IN (:injectIds)
        AND jsonb_path_exists(
          s.step_data,
          '$.** ? (@.inject_id == $id)',
          jsonb_build_object('id', to_jsonb(i.inject_id))
        )
      )
      """,
      nativeQuery = true)
  Set<String> findStepIdsByInjectIds(@Param("injectIds") Set<String> injectIds);

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

  List<Step> findAllStepByWorkflow_IdAndStatusIn(String id, List<StepStatus> run);

  /**
   * Returns {@code true} if at least one executed step references the given step template within
   * the given workflow run.
   *
   * @param stepTemplateId the ID of the step template to check
   * @param workflowRunId the ID of the workflow run to scope the check
   * @return {@code true} if a matching step exists
   */
  boolean existsByStepTemplateIdAndWorkflowId(String stepTemplateId, String workflowRunId);
}
