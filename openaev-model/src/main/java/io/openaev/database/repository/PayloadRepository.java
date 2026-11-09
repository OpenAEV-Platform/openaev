package io.openaev.database.repository;

import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Limit;
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

  Optional<Payload> findByIdentityExternalId(@NotNull String externalId);

  List<Payload> findAllByTenantIdAndContentHash(String tenantId, String contentHash);

  List<Payload> findAllByTenantIdAndNameAndContentHashIsNull(String tenantId, String name);

  /** Where injects run this payload: their scenario, simulation, or atomic testing title. */
  @Query(
      "SELECT DISTINCT COALESCE(s.name, e.name, i.title) FROM Inject i"
          + " LEFT JOIN i.scenario s LEFT JOIN i.exercise e"
          + " WHERE i.injectorContract.payload.id = :payloadId")
  List<String> findUsageNamesByPayloadId(@Param("payloadId") String payloadId, Limit limit);

  /**
   * Tenant-scoped counterpart of {@link #findByIdentityExternalId}: an external id repeats across
   * the tenants that hold a copy of the same payload, so a caller whose scope spans several tenants
   * resolves the one of the tenant it writes in.
   */
  @Query(
      "SELECT p FROM Payload p WHERE p.identity.externalId = :externalId AND p.tenant.id ="
          + " :tenantId")
  Optional<Payload> findByExternalIdAndTenantId(
      @Param("externalId") @NotNull String externalId, @Param("tenantId") @NotNull String tenantId);

  /**
   * Icon metadata for a batch of payloads in one read: {@code [id, payloadType,
   * collectorTypeName]}, the collector type name being null for a hand-authored payload with no
   * backing collector. Lets the attack-path graph resolve each agent-executed action's real catalog
   * icon (the collector logo, e.g. netexec / atomic-red-team) instead of the generic agent icon.
   */
  @Query(
      "SELECT p.id, p.type, ct.name FROM Payload p JOIN p.identity pi"
          + " LEFT JOIN pi.collectorType ct WHERE p.id IN :ids")
  List<Object[]> findIconMetadataByIds(@Param("ids") Set<String> ids);

  @Query(
      value =
          "SELECT pi.payload_external_id FROM payload_identities pi"
              + " JOIN collectors c ON c.collector_type_id = pi.payload_collector_type"
              + " WHERE c.collector_id = :collectorId"
              + " AND pi.tenant_id = :tenantId AND c.tenant_id = :tenantId",
      nativeQuery = true)
  List<String> findAllExternalIdsByCollectorId(
      @NotNull @Param("collectorId") String collectorId, @Param("tenantId") String tenantId);

  @Modifying
  @Query(
      value =
          "UPDATE payloads p SET payload_status = :payloadStatus FROM payload_identities pi"
              + " WHERE pi.payload_identity_id = p.payload_identity AND pi.payload_external_id IN :payloadExternalIds"
              + " AND p.tenant_id = :tenantId AND pi.tenant_id = :tenantId",
      nativeQuery = true)
  void setPayloadStatusByExternalIds(
      @Param("payloadStatus") String payloadStatus,
      @Param("payloadExternalIds") List<String> payloadExternalIds,
      @Param("tenantId") String tenantId);

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
}
