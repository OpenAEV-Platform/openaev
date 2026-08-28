package io.openaev.database.repository;

import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.SecurityPlatform;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DetectionRemediationRepository
    extends CrudRepository<DetectionRemediation, String>,
        JpaSpecificationExecutor<DetectionRemediation> {
  @Query(
      """
              SELECT DISTINCT dr.securityPlatform FROM DetectionRemediation dr
              WHERE dr.payload.id = :payloadId
          """)
  List<SecurityPlatform> findSecurityPlatformsByPayloadId(@Param("payloadId") String payloadId);

  @Query(
      """
              SELECT DISTINCT dr.securityPlatform
              FROM Inject i
              JOIN i.injectorContract ic
              JOIN ic.payload p
              JOIN p.detectionRemediations dr
              WHERE i.id = :injectId
          """)
  List<SecurityPlatform> findSecurityPlatformsByInjectId(@Param("injectId") String injectId);

  @Query(
      value =
          "SELECT dr.detection_remediation_id AS id, "
              + "dr.detection_remediation_values AS values, "
              + "dr.author_rule AS authorRule, "
              + "dr.detection_remediation_collector_type AS collectorType, "
              + "dr.detection_remediation_security_platform AS securityPlatformId "
              + "FROM detection_remediations dr "
              + "WHERE dr.detection_remediation_payload_id = :payloadId",
      nativeQuery = true)
  List<SnapshotRow> findSnapshotRowsByPayloadId(@Param("payloadId") String payloadId);

  interface SnapshotRow {
    String getId();

    String getValues();

    DetectionRemediation.AUTHOR_RULE getAuthorRule();

    String getCollectorType();

    String getSecurityPlatformId();
  }
}
