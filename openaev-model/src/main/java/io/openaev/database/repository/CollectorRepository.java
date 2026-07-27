package io.openaev.database.repository;

import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorCompositeId;
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
    extends CrudRepository<Collector, ConnectorCompositeId>, JpaSpecificationExecutor<Collector> {

  Optional<Collector> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  /** Finds a collector by its ID only. Tenant scoping is handled by the v2 SQL inspector. */
  @Query("SELECT c FROM Collector c WHERE c.id = :id")
  Optional<Collector> findByCollectorId(@Param("id") @NotNull String id);

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
  /**
   * Deletes a collector by its ID only. Tenant scoping is handled by the v2 SQL inspector, which
   * rewrites this DELETE the same way it rewrites SELECTs on active tables.
   */
  @Modifying
  @Query("DELETE FROM Collector c WHERE c.id = :id")
  void deleteByCollectorId(@Param("id") String id);

  /**
   * Native query to bypass Hibernate's @Filter("tenantFilter"). This is called from
   * InjectsExecutionJob (background scheduler) via buildAndSaveInjectExpectations(). InjectHelper
   * disables the tenant filter at the start of the job, but the @Transactional on
   * buildAndSaveInjectExpectations() re-enables it via HibernateFilterTransactionAspect with a
   * potentially different TenantContext. Two options exist: disableFilter before the call, or use a
   * native query. Native query is more robust as it is immune to filter re-activation.
   */
  @Query(
      nativeQuery = true,
      value =
          "SELECT * FROM collectors WHERE tenant_id = :tenantId AND collector_security_platform IS NOT NULL")
  List<Collector> findAllByTenantIdAndSecurityPlatformIsNotNull(
      @Param("tenantId") @NotNull String tenantId);
}
