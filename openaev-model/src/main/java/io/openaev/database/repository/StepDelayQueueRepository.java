package io.openaev.database.repository;

import io.openaev.database.model.StepDelayQueue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
}
