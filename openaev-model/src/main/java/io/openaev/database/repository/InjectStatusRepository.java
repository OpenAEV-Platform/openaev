package io.openaev.database.repository;

import io.openaev.database.model.InjectStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectStatusRepository
    extends CrudRepository<InjectStatus, String>, JpaSpecificationExecutor<InjectStatus> {

  @NotNull
  Optional<InjectStatus> findById(@NotNull String id);

  Optional<InjectStatus> findByInjectId(@NotNull String injectId);

  @Query(
      value =
          "SELECT ins.*, t.*"
              + " FROM injects_statuses ins"
              + " INNER JOIN injects i ON ins.status_inject = i.inject_id"
              + " LEFT JOIN execution_traces t"
              + "  ON t.execution_inject_status_id = ins.status_id"
              + "  AND t.execution_agent_id IS NULL"
              + "  AND cardinality(t.execution_context_identifiers) = 0"
              + " WHERE i.inject_id = :injectId"
              + " ORDER BY t.execution_time ASC, t.execution_trace_id ASC",
      nativeQuery = true)
  Optional<InjectStatus> findInjectStatusWithGlobalExecutionTraces(String injectId);

  /**
   * The execution status name of many injects at once, as {@code (injectId, statusName)} pairs. The
   * attack-path graph read ships every execution's "did it actually run" status, so resolving it
   * per row would be one query per visible execution. Injects with no status row yet simply do not
   * appear.
   *
   * @param injectIds the injects whose status name is wanted
   * @return one row per inject that has a status: {@code [injectId, statusName]}
   */
  @Query(
      "SELECT istatus.inject.id, istatus.name FROM InjectStatus istatus"
          + " WHERE istatus.inject.id IN :injectIds AND istatus.name IS NOT NULL")
  List<Object[]> findStatusNamesByInjectIds(@Param("injectIds") Collection<String> injectIds);

  @Modifying(clearAutomatically = true)
  @Query("delete from InjectStatus i where i.id in :ids")
  void deleteAllByIds(@Param("ids") List<String> ids);

  @Query(
      "SELECT COUNT(DISTINCT istatus.inject.id) FROM InjectStatus istatus"
          + " WHERE istatus.inject.exercise.id = :simulationId"
          + " AND istatus.trackingSentDate >= :since")
  long countLaunchedInjectsSince(
      @Param("simulationId") String simulationId, @Param("since") Instant since);
}
