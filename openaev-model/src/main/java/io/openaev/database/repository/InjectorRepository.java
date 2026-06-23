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

  @Query(
      value =
          """
        SELECT * FROM injectors
        WHERE injector_id = :id
          AND tenant_id = :tenantId
        """,
      nativeQuery = true)
  Optional<Injector> getByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

  @NotNull
  Optional<Injector> findByTypeAndTenantId(@NotNull String type, @NotNull String tenantId);

  List<Injector> findAllByPayloadsAndTenantId(@NotNull Boolean payloads, @NotNull String tenantId);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM injectors WHERE injector_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

  /**
   * Updates builtin injector scalar properties scoped to a single tenant. Avoids the
   * BatchedTooManyRowsAffectedException caused by Hibernate's single-column WHERE clause
   * (injector_id) when the DB has a composite PK (injector_id, tenant_id).
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE injectors SET
            injector_name             = :name,
            injector_type             = :type,
            injector_category         = :category,
            injector_external         = false,
            injector_custom_contracts = :customContracts,
            injector_payloads         = :payloads,
            injector_updated_at       = now()
          WHERE injector_id = :id AND tenant_id = :tenantId
          """)
  void updateBuiltinScalarProperties(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("name") String name,
      @Param("type") String type,
      @Param("category") String category,
      @Param("customContracts") boolean customContracts,
      @Param("payloads") boolean payloads);

  /**
   * Idempotently links an injector contract to this injector in the join table. ON CONFLICT DO
   * NOTHING makes repeated calls safe.
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO injectors_injector_contracts (injector_id, injector_contract_id, tenant_id)
          VALUES (:injectorId, :contractId, :tenantId)
          ON CONFLICT DO NOTHING
          """)
  void linkContract(
      @Param("injectorId") String injectorId,
      @Param("contractId") String contractId,
      @Param("tenantId") String tenantId);
}
