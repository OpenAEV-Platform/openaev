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

  /**
   * Updates executor fields using a native query with composite WHERE (executor_id + tenant_id).
   *
   * <p>Must be used instead of relying on Hibernate dirty-checking, because the executors table has
   * rows sharing the same executor_id across tenants. Hibernate's generated UPDATE uses only the
   * {@code @Id} column (executor_id) in the WHERE clause, which would match multiple rows and cause
   * {@code BatchedTooManyRowsAffectedException}.
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE executors
          SET executor_name            = :name,
              executor_type            = :type,
              executor_doc             = :doc,
              executor_background_color = :backgroundColor,
              executor_platforms       = CAST(:platforms AS text[]),
              executor_updated_at      = now()
          WHERE executor_id = :id AND tenant_id = :tenantId
          """)
  void updateByIdAndTenantId(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("name") String name,
      @Param("type") String type,
      @Param("doc") String doc,
      @Param("backgroundColor") String backgroundColor,
      @Param("platforms") String platforms);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM executors WHERE executor_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);
}
