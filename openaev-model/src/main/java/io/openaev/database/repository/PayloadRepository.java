package io.openaev.database.repository;

import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadRepository
    extends CrudRepository<Payload, String>, JpaSpecificationExecutor<Payload> {
  @NotNull
  Optional<Payload> findById(@NotNull String id);

  /**
   * Tenant-scoped counterpart of {@link #findById}: a PK load bypasses the Hibernate tenant filter,
   * so user-supplied ids (e.g. import payloads) must be resolved through this method instead.
   */
  @Query("SELECT p FROM Payload p WHERE p.id = :id AND p.tenant.id = :tenantId")
  Optional<Payload> findByIdAndTenantId(
      @Param("id") @NotNull String id, @Param("tenantId") @NotNull String tenantId);

  /** Several versions can share an external ID: resolve through PayloadVersionService. */
  List<Payload> findAllByExternalId(@NotNull String externalId);

  Optional<Payload> findByExternalIdAndLatestTrue(@NotNull String externalId);

  @Query(
      "SELECT COUNT(p) > 0 FROM Payload p WHERE LOWER(TRIM(p.name)) = LOWER(TRIM(:name))"
          + " AND (:payloadId IS NULL OR p.id <> :payloadId)"
          + " AND (:externalId IS NULL OR p.externalId IS NULL OR p.externalId <> :externalId)")
  boolean existsOtherPayloadWithName(
      @Param("name") String name,
      @Param("payloadId") String payloadId,
      @Param("externalId") String externalId);

  @Query("SELECT p FROM Payload p WHERE p.contentHash = :hash AND p.tenant.id = :tenantId")
  List<Payload> findAllByContentHash(
      @Param("hash") String hash, @Param("tenantId") String tenantId);

  @Query(
      "SELECT c.payload FROM InjectorContract c"
          + " WHERE c.compositeId.id = :contractId AND c.compositeId.tenantId = :tenantId")
  Optional<Payload> findByInjectorContractId(
      @Param("contractId") String contractId, @Param("tenantId") String tenantId);

  /** Scenario or simulation name per inject running this payload; null for an atomic test. */
  @Query(
      "SELECT DISTINCT COALESCE(s.name, e.name) FROM Inject i"
          + " LEFT JOIN i.scenario s LEFT JOIN i.exercise e"
          + " WHERE i.injectorContract.payload.id = :payloadId")
  List<String> findUsageNamesByPayloadId(@Param("payloadId") String payloadId);

  @Query(
      "SELECT c.compositeId.id FROM InjectorContract c JOIN c.payload p"
          + " WHERE c.compositeId.id IN :contractIds"
          + " AND (p.version IS NOT NULL"
          + " OR EXISTS (SELECT 1 FROM Inject i WHERE i.injectorContract = c))")
  Set<String> findNotDeletableContractIds(@Param("contractIds") Collection<String> contractIds);

  /**
   * Icon metadata for a batch of payloads in one read: {@code [id, payloadType,
   * collectorTypeName]}, the collector type name being null for a hand-authored payload with no
   * backing collector. Lets the attack-path graph resolve each agent-executed action's real catalog
   * icon (the collector logo, e.g. netexec / atomic-red-team) instead of the generic agent icon.
   */
  @Query(
      "SELECT p.id, p.type, ct.name FROM Payload p LEFT JOIN p.collectorType ct WHERE p.id IN :ids")
  List<Object[]> findIconMetadataByIds(@Param("ids") Set<String> ids);

  @Query(
      value =
          "SELECT p.payload_external_id FROM payloads p"
              + " JOIN collectors c ON c.collector_type_id = p.payload_collector_type"
              + " WHERE c.collector_id = :collectorId",
      nativeQuery = true)
  List<String> findAllExternalIdsByCollectorId(@NotNull @Param("collectorId") String collectorId);

  @Modifying
  @Query(
      value =
          "UPDATE payloads SET payload_status = :payloadStatus WHERE payload_external_id IN :payloadExternalIds AND payload_version IS NULL AND tenant_id = :#{#tenantContext.currentTenant}",
      nativeQuery = true)
  void setPayloadStatusByExternalIds(
      @Param("payloadStatus") String payloadStatus,
      @Param("payloadExternalIds") List<String> payloadExternalIds);

  @Query(
      """
          SELECT dr
          FROM Inject inj
          JOIN inj.injectorContract ic
          JOIN ic.payload p
          JOIN DetectionRemediation dr ON dr.payload = p
          WHERE inj.id = :injectId
      """)
  List<DetectionRemediation> fetchDetectionRemediationsByInjectId(String injectId);

  @Query("select fd from FileDrop fd where fd.fileDropFile.id = :documentId")
  Optional<FileDrop> findByDocumentId(@Param("documentId") final String documentId);

  /**
   * Returns the payloads with the given name that are not referenced by any injector contract,
   * scoped to a tenant. A (now fixed) regression in the starter-pack import left such payloads
   * orphaned on fresh platforms (their contract was persisted without the payload reference); the
   * {@code V20260725_Fix_starter_pack_payload_contracts} runtime migration re-attaches them by name
   * (payload contracts are labeled with their payload name).
   */
  @Query(
      "SELECT p FROM Payload p "
          + "WHERE p.name = :name AND p.tenant.id = :tenantId "
          + "AND NOT EXISTS (SELECT ic FROM InjectorContract ic WHERE ic.payload = p)")
  List<Payload> findOrphansByNameAndTenantId(
      @Param("name") String name, @Param("tenantId") String tenantId);

  /** Installed managed payload versions, as {external id, version} pairs (F5). */
  @Query(
      "SELECT p.externalId, p.version FROM Payload p WHERE p.externalId IS NOT NULL"
          + " AND p.version IS NOT NULL")
  List<Object[]> findInstalledManagedVersions();
}
