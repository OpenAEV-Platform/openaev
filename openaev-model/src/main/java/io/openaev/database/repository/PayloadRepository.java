package io.openaev.database.repository;

import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
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
}
