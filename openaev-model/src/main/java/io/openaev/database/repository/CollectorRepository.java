package io.openaev.database.repository;

import io.openaev.database.model.Collector;
import io.openaev.database.model.CollectorId;
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
public interface CollectorRepository
    extends JpaRepository<Collector, CollectorId>, JpaSpecificationExecutor<Collector> {

  /**
   * ⛔ FORBIDDEN — This entity is tenant-scoped. Use {@link #findAllByCompositeIdTenantId(String)}
   * instead.
   */
  @Override
  default List<Collector> findAll() {
    throw new UnsupportedOperationException(
        "findAll() is forbidden on CollectorRepository (tenant-scoped). "
            + "Use findAllByCompositeIdTenantId(tenantId) instead.");
  }

  List<Collector> findAllByCompositeIdTenantId(@NotNull String tenantId);

  /**
   * Finds a collector by its logical ID and tenant. Delegates to {@link #findById(Object)} with a
   * composite key.
   */
  default Optional<Collector> findById(@NotNull String id, @NotNull String tenantId) {
    return findById(new CollectorId(id, tenantId));
  }

  Optional<Collector> findByTypeAndCompositeIdTenantId(
      @NotNull String type, @NotNull String tenantId);

  @Query(
      """
              SELECT DISTINCT c FROM Collector c
              WHERE c.compositeId.tenantId = :tenantId
              AND c.collectorType IN (
                  SELECT dr.collectorType FROM DetectionRemediation dr
                  JOIN dr.payload p
                  WHERE p.id = :payloadId
              )
          """)
  List<Collector> findByPayloadIdAndTenantId(
      @Param("payloadId") String payloadId, @Param("tenantId") String tenantId);

  @Query(
      """
              SELECT DISTINCT c FROM Collector c
              WHERE c.compositeId.tenantId = :tenantId
              AND c.collectorType IN (
                  SELECT dr.collectorType
                  FROM Inject i
                  JOIN i.injectorContract ic
                  JOIN ic.payload p
                  JOIN p.detectionRemediations dr
                  WHERE i.id = :injectId
              )
          """)
  List<Collector> findByInjectIdAndTenantId(
      @Param("injectId") String injectId, @Param("tenantId") String tenantId);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM collectors WHERE collector_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);
}
