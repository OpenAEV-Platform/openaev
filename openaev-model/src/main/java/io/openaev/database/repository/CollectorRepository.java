package io.openaev.database.repository;

import io.openaev.database.model.Collector;
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
public interface CollectorRepository
    extends CrudRepository<Collector, String>, JpaSpecificationExecutor<Collector> {

  Optional<Collector> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  @Query(
      """
              SELECT DISTINCT c FROM Collector c
              WHERE c.collectorType IN (
                  SELECT dr.collectorType FROM DetectionRemediation dr
                  JOIN dr.payload p
                  WHERE p.id = :payloadId
              )
          """)
  List<Collector> findByPayloadId(@Param("payloadId") String payloadId);

  @Query(
      """
              SELECT DISTINCT c FROM Collector c
              WHERE c.collectorType IN (
                  SELECT dr.collectorType
                  FROM Inject i
                  JOIN i.injectorContract ic
                  JOIN ic.payload p
                  JOIN p.detectionRemediations dr
                  WHERE i.id = :injectId
              )
          """)
  List<Collector> findByInjectId(@Param("injectId") String injectId);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM collectors WHERE collector_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);
}
