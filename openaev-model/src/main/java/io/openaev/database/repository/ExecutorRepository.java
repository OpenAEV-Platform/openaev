package io.openaev.database.repository;

import io.openaev.database.model.Executor;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutorRepository extends CrudRepository<Executor, String> {

  Optional<Executor> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  @NotNull
  Optional<Executor> findByTypeAndTenantId(@NotNull String type, @NotNull String tenantId);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM executors WHERE executor_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);
}
