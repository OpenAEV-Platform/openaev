package io.openaev.database.repository;

import io.openaev.database.model.ConnectorCompositeId;
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
    extends JpaRepository<Injector, ConnectorCompositeId>, JpaSpecificationExecutor<Injector> {

  Optional<Injector> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  boolean existsByTypeAndTenantId(@NotNull String type, @NotNull String tenantId);

  @Query(
      "SELECT i FROM Injector i "
          + "WHERE i.type = :externalReference AND i.tenantId = :tenantId "
          + "ORDER BY i.id")
  List<Injector> findBySecurityPlatformExternalReferenceByTenantId(
      @Param("externalReference") @NotNull String externalReference,
      @Param("tenantId") @NotNull String tenantId);

  @Query(
      "SELECT i FROM Injector i "
          + "WHERE i.type = :contractType AND i.tenantId = :tenantId "
          + "ORDER BY i.id")
  List<Injector> findByPhishingContractTypeByTenantId(
      @Param("contractType") @NotNull String contractType,
      @Param("tenantId") @NotNull String tenantId);

  @Query(
      "SELECT l.injector FROM InjectorInjectorContract l "
          + "WHERE l.injectorContractId = :contractId AND l.tenantId = :tenantId "
          + "ORDER BY l.injectorId")
  List<Injector> findInjectorsLinkedToContract(
      @Param("contractId") String contractId, @Param("tenantId") String tenantId);

  /**
   * Resolves an injector linked to the given contract within the tenant. Kept under its original
   * name for callers; the association is now expressed over the {@link
   * io.openaev.database.model.InjectorInjectorContract} join entity, since {@code
   * Injector.contracts} is derived from the join table rather than a mapped collection.
   */
  default Optional<Injector> findFirstByContractsCompositeIdIdAndTenantId(
      String contractId, String tenantId) {
    return findInjectorsLinkedToContract(contractId, tenantId).stream()
        .filter(injector -> injector != null)
        .findFirst();
  }

  List<Injector> findAllByPayloadsAndTenantId(@NotNull Boolean payloads, @NotNull String tenantId);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM injectors WHERE injector_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

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
