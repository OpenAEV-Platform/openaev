package io.openaev.database.repository;

import io.openaev.database.model.ExecutionTrace;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ExecutionTraceRepository
    extends CrudRepository<ExecutionTrace, String>, JpaSpecificationExecutor<ExecutionTrace> {

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "INNER JOIN Agents a ON t.execution_agent_id = a.agent_id "
              + "WHERE i.inject_id = :injectId AND t.execution_agent_id = :targetId "
              + "ORDER BY t.execution_time ASC, t.execution_trace_id ASC",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndAgentId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "LEFT JOIN Agents a ON t.execution_agent_id = a.agent_id "
              + "WHERE i.inject_id = :injectId AND (a.agent_asset = :targetId OR :targetId = ANY(t.execution_context_identifiers)) "
              + "ORDER BY t.execution_time ASC, t.execution_trace_id ASC",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndAssetId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "INNER JOIN users_teams ut ON ut.user_id = ANY(t.execution_context_identifiers) "
              + "WHERE i.inject_id = :injectId AND ut.team_id = :targetId "
              + "ORDER BY t.execution_time ASC, t.execution_trace_id ASC",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndTeamId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "WHERE i.inject_id = :injectId AND :targetId = ANY(t.execution_context_identifiers) "
              + "ORDER BY t.execution_time ASC, t.execution_trace_id ASC",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndPlayerId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  /**
   * The status and agent of every COMPLETE trace of the given injects, one row per trace the reader
   * may see: {@code [injectId, ExecutionTraceStatus, agentId or null]}. The marking filter on
   * {@code execution_traces} drops the traces of agents above the reader's clearance.
   *
   * <p>The action is a literal, not a parameter, so the planner can always use the partial index
   * {@code idx_execution_traces_complete_inject_status}: a cached generic plan does not know the
   * value of a parameter, and so cannot prove the index's {@code WHERE execution_action =
   * 'COMPLETE'}.
   */
  @Query(
      "SELECT s.inject.id, t.status, t.agent.id FROM ExecutionTrace t JOIN t.injectStatus s"
          + " WHERE s.inject.id IN :injectIds"
          + " AND t.action = io.openaev.database.model.ExecutionTraceAction.COMPLETE")
  List<Object[]> findCompleteStatusesAndAgentsByInjectIds(
      @Param("injectIds") Collection<String> injectIds);

  /**
   * Deletes one bounded batch of execution traces older than the given threshold. Batched so the
   * retention job never holds a long transaction or large lock set on this hot-write table.
   *
   * @param threshold traces created strictly before this instant are deleted
   * @param batchSize maximum number of traces deleted in this batch
   * @return the number of deleted traces
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "DELETE FROM execution_traces WHERE execution_trace_id IN ("
              + "SELECT execution_trace_id FROM execution_traces "
              + "WHERE execution_created_at < :threshold LIMIT :batchSize)",
      nativeQuery = true)
  int deleteBatchOlderThan(
      @Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
