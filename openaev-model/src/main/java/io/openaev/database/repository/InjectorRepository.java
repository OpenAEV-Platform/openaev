package io.openaev.database.repository;

import io.openaev.database.model.Injector;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectorRepository
    extends JpaRepository<Injector, String>, JpaSpecificationExecutor<Injector> {

  Optional<Injector> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  @NotNull
  Optional<Injector> findByTypeAndTenantId(@NotNull String type, @NotNull String tenantId);

  List<Injector> findAllByPayloadsAndTenantId(@NotNull Boolean payloads, @NotNull String tenantId);

  /**
   * Updates injector scalar fields via a native query that explicitly includes {@code tenant_id} in
   * the WHERE clause. Injectors share the same {@code injector_id} across tenants; Hibernate's
   * generated UPDATE only uses {@code @Id} (injector_id) and would affect every tenant's row,
   * causing {@code BatchedTooManyRowsAffectedException}. This query avoids that by being scoped.
   *
   * <p>{@code executorCommands} and {@code executorClearCommands} must be pre-serialized to
   * PostgreSQL hstore format (e.g. {@code "k1"=>"v1","k2"=>"v2"} or empty string for null). {@code
   * dependencies} must be pre-serialized to a PostgreSQL text-array literal (e.g. {@code {V1,V2}}
   * or {@code {}}).
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE injectors
          SET injector_name                    = :name,
              injector_type                    = :type,
              injector_category                = :category,
              injector_external                = false,
              injector_custom_contracts        = :customContracts,
              injector_executor_commands       = CAST(:executorCommands AS hstore),
              injector_executor_clear_commands = CAST(:executorClearCommands AS hstore),
              injector_payloads                = :payloads,
              injector_dependencies            = CAST(:dependencies AS text[]),
              injector_updated_at              = now()
          WHERE injector_id = :id AND tenant_id = :tenantId
          """)
  void updateScalarFieldsByIdAndTenantId(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("name") String name,
      @Param("type") String type,
      @Param("category") String category,
      @Param("customContracts") boolean customContracts,
      @Param("executorCommands") String executorCommands,
      @Param("executorClearCommands") String executorClearCommands,
      @Param("payloads") boolean payloads,
      @Param("dependencies") String dependencies);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM injectors WHERE injector_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);
}
