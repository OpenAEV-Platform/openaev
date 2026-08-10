package io.openaev.database.repository;

import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import jakarta.validation.constraints.NotNull;
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

  Optional<Payload> findByExternalId(@NotNull String externalId);

  /**
   * Icon metadata for a batch of payloads in one read: {@code [id, payloadType,
   * collectorTypeName]}, the collector type name being null for a hand-authored payload with no
   * backing collector. Lets the attack-path graph resolve each agent-executed action's real catalog
   * icon (the collector logo, e.g. netexec / atomic-red-team) instead of the generic agent icon.
   */
  @Query("SELECT p.id, p.type, ct.name FROM Payload p LEFT JOIN p.collectorType ct WHERE p.id IN :ids")
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
          "UPDATE payloads SET payload_status = :payloadStatus WHERE payload_external_id IN :payloadExternalIds AND tenant_id = :#{#tenantContext.currentTenant}",
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
}
