package io.openaev.database.repository;

import io.openaev.database.model.InjectStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectStatusRepository
    extends CrudRepository<InjectStatus, String>, JpaSpecificationExecutor<InjectStatus> {

  @NotNull
  Optional<InjectStatus> findById(@NotNull String id);

  @Query(
      value =
          "select c from InjectStatus c where c.name = 'PENDING' and c.inject.injectorContract.injector.type = :injectType")
  List<InjectStatus> pendingForInjectType(@Param("injectType") String injectType);

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
              + " WHERE i.inject_id = :injectId",
      nativeQuery = true)
  Optional<InjectStatus> findInjectStatusWithGlobalExecutionTraces(String injectId);
}
