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

  @NotNull
  Optional<Executor> findById(@NotNull String id);

  @NotNull
  Optional<Executor> findByTypeAndTenantId(@NotNull String type, @NotNull String tenantId);

  @Modifying
  @Query(value = "DELETE FROM executors WHERE tenant_id = :tenantId", nativeQuery = true)
  void deleteAllByTenantIdNative(@Param("tenantId") String tenantId);
}
