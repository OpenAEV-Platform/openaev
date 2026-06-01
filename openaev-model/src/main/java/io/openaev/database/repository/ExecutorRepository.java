package io.openaev.database.repository;

import io.openaev.database.model.Executor;
import io.openaev.database.model.ExecutorId;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutorRepository extends JpaRepository<Executor, ExecutorId> {

  /**
   * ⛔ FORBIDDEN — This entity is tenant-scoped. Use {@link #findAllByCompositeIdTenantId(String)}
   * instead.
   */
  @Override
  default List<Executor> findAll() {
    throw new UnsupportedOperationException(
        "findAll() is forbidden on ExecutorRepository (tenant-scoped). "
            + "Use findAllByCompositeIdTenantId(tenantId) instead.");
  }

  List<Executor> findAllByCompositeIdTenantId(@NotNull String tenantId);

  /**
   * Finds an executor by its logical ID and tenant. Delegates to {@link #findById(Object)} with a
   * composite key.
   */
  default Optional<Executor> findById(@NotNull String id, @NotNull String tenantId) {
    return findById(new ExecutorId(id, tenantId));
  }

  @NotNull
  Optional<Executor> findByTypeAndCompositeIdTenantId(
      @NotNull String type, @NotNull String tenantId);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM executors WHERE executor_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);
}
