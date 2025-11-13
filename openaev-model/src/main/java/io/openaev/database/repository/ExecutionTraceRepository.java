package io.openaev.database.repository;

import io.openaev.database.model.ExecutionTrace;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionTraceRepository
    extends CrudRepository<ExecutionTrace, String>, JpaSpecificationExecutor<ExecutionTrace> {

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "INNER JOIN Agents a ON t.execution_agent_id = a.agent_id "
              + "WHERE i.inject_id = :injectId AND t.execution_agent_id = :targetId",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndAgentId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "LEFT JOIN Agents a ON t.execution_agent_id = a.agent_id "
              + "WHERE i.inject_id = :injectId AND (a.agent_asset = :targetId OR :targetId = ANY(t.execution_context_identifiers))",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndAssetId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "INNER JOIN users_teams ut ON ut.user_id = ANY(t.execution_context_identifiers) "
              + "WHERE i.inject_id = :injectId AND ut.team_id = :targetId",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndTeamId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN injects i ON ins.status_inject = i.inject_id "
              + "WHERE i.inject_id = :injectId AND :targetId = ANY(t.execution_context_identifiers)",
      nativeQuery = true)
  List<ExecutionTrace> findByInjectIdAndPlayerId(
      @Param("injectId") String injectId, @Param("targetId") String targetId);

  @Modifying
  @Query(
      value =
          """
        INSERT INTO execution_traces (
            execution_trace_id,
            execution_inject_status_id,
            execution_inject_test_status_id,
            execution_agent_id,
            execution_message,
            execution_structured_output,
            execution_action,
            execution_status,
            execution_time,
            execution_context_identifiers,
            execution_created_at,
            execution_updated_at
        ) VALUES (
            gen_random_uuid(),
            :injectStatusId,
            :injectTestStatusId,
            :agentId,
            :message,
            :structuredOutput,
            :action,
            :status,
            :time,
            :identifiers,
            :now,
            :now
        )
        """,
      nativeQuery = true)
  void simpleSave(
      @Param("injectStatusId") String injectStatusId,
      @Param("injectTestStatusId") String injectTestStatusId,
      @Param("agentId") String agentId,
      @Param("message") String message,
      @Param("structuredOutput") String structuredOutput,
      @Param("action") String action,
      @Param("status") String status,
      @Param("time") Instant time,
      @Param("identifiers") List<String> identifiers,
      @Param("now") Instant now);
}
