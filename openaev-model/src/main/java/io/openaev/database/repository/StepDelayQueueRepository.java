package io.openaev.database.repository;

import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StepDelayQueueRepository extends JpaRepository<StepDelayQueue, String> {
  @Modifying
  @Query(
      value =
          """
    WITH next_per_run AS (
        SELECT DISTINCT ON (steps_delay_queue_workflow_run_id) steps_delay_queue_id
        FROM steps_delay_queue
        WHERE steps_delay_queue_goal <= now()
        ORDER BY steps_delay_queue_workflow_run_id, steps_delay_queue_goal
    )
    DELETE FROM steps_delay_queue
    WHERE steps_delay_queue_id IN (SELECT steps_delay_queue_id FROM next_per_run)
    RETURNING *
    """,
      nativeQuery = true)
  List<StepDelayQueue> popNextPerWorkflowRun();

  List<StepDelayQueue> findAllByWorkflowRun(Workflow workflowRun);

  int deleteAllByWorkflowRun(Workflow workflowRun);

  // Native upsert is required here to keep insert/update atomic under concurrency.
  @Modifying
  @Query(
      value =
          """
          INSERT INTO steps_delay_queue (
            steps_delay_queue_input,
            steps_delay_queue_now,
            steps_delay_queue_goal,
            steps_delay_queue_delay,
            steps_delay_queue_step_template_id,
            steps_delay_queue_workflow_run_id,
            steps_delay_queue_created_at,
            steps_delay_queue_updated_at
          ) VALUES (
            :input,
            :now,
            :goal,
            :delay,
            :stepTemplateId,
            :workflowRunId,
            now(),
            now()
          )
          ON CONFLICT (
            steps_delay_queue_workflow_run_id,
            steps_delay_queue_step_template_id,
            (COALESCE(steps_delay_queue_input, ''::text))
          )
          DO UPDATE
          SET
            steps_delay_queue_now = EXCLUDED.steps_delay_queue_now,
            steps_delay_queue_goal = EXCLUDED.steps_delay_queue_goal,
            steps_delay_queue_delay = EXCLUDED.steps_delay_queue_delay,
            steps_delay_queue_updated_at = now()
          """,
      nativeQuery = true)
  void upsertByWorkflowRunStepTemplateAndInput(
      @Param("input") String input,
      @Param("now") Instant now,
      @Param("goal") Instant goal,
      @Param("delay") Long delay,
      @Param("stepTemplateId") String stepTemplateId,
      @Param("workflowRunId") String workflowRunId);
}
