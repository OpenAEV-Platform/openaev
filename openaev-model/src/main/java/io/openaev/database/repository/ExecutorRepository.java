package io.openaev.database.repository;

import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.model.Executor;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutorRepository extends CrudRepository<Executor, ConnectorCompositeId> {

  @Query("SELECT e FROM Executor e WHERE e.id = :id")
  Optional<Executor> findByExecutorId(@Param("id") @NotNull String id);

  // executor_id alone is not unique (composite PK with tenant_id): pin the tenant when the caller
  // knows it, so the Optional cannot blow up on several tenants owning the same built-in executor.
  @Query("SELECT e FROM Executor e WHERE e.id = :id AND e.tenantId = :tenantId")
  Optional<Executor> findByExecutorIdAndTenantId(
      @Param("id") @NotNull String id, @Param("tenantId") @NotNull String tenantId);

  Optional<Executor> findByType(@NotNull String type);

  @Modifying
  @Query("DELETE FROM Executor e WHERE e.id = :id")
  void deleteByExecutorId(@Param("id") String id);
}
