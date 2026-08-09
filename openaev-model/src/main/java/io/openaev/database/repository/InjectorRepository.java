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

  @Query("SELECT i FROM Injector i WHERE i.id = :id")
  Optional<Injector> findByInjectorId(@Param("id") @NotNull String id);

  @Deprecated(forRemoval = false)
  @Query("SELECT i FROM Injector i WHERE i.id = :id AND i.tenantId = :tenantId")
  Optional<Injector> findByIdAndTenantId(
      @Param("id") @NotNull String id, @Param("tenantId") @NotNull String tenantId);

  Optional<Injector> findByType(@NotNull String type);

  @Deprecated(forRemoval = false)
  @Query("SELECT i FROM Injector i WHERE i.type = :type AND i.tenantId = :tenantId")
  Optional<Injector> findByTypeAndTenantId(
      @Param("type") @NotNull String type, @Param("tenantId") @NotNull String tenantId);

  @Query(
      "SELECT l.injector FROM InjectorInjectorContract l "
          + "WHERE l.injectorContractId = :contractId "
          + "ORDER BY l.injectorId")
  List<Injector> findInjectorsLinkedToContract(@Param("contractId") String contractId);

  default Optional<Injector> findFirstByContractsCompositeIdIdAndTenantId(
      String contractId, String tenantId) {
    return findInjectorsLinkedToContract(contractId).stream()
        .filter(injector -> injector != null)
        .filter(injector -> tenantId.equals(injector.getTenantId()))
        .findFirst();
  }

  List<Injector> findAllByPayloads(@NotNull Boolean payloads);

  @Query("SELECT i FROM Injector i WHERE i.id IN :ids")
  List<Injector> findAllByInjectorIdIn(@Param("ids") List<String> ids);

  @Modifying
  @Query("DELETE FROM Injector i WHERE i.id = :id")
  void deleteByInjectorId(@Param("id") String id);

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
