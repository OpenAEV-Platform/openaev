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
}
