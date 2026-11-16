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

  /**
   * Deletes a collector by its ID only. Tenant scoping is handled by the v2 SQL inspector, which
   * rewrites this DELETE the same way it rewrites SELECTs on active tables.
   */
  @Modifying
  @Query("DELETE FROM Collector c WHERE c.id = :id")
  void deleteByCollectorId(@Param("id") String id);

  /**
   * Native query with an explicit tenant, so no Hibernate filter applies. This is called from
   * InjectsExecutionJob (background scheduler) via buildAndSaveInjectExpectations(), whose
   * {@code @Transactional} arms the v1 filter through HibernateFilterTransactionAspect with
   * whatever TenantContext the thread holds.
   */
  @Query(
      nativeQuery = true,
      value =
          "SELECT * FROM collectors WHERE tenant_id = :tenantId AND collector_security_platform IS NOT NULL")
  List<Collector> findAllByTenantIdAndSecurityPlatformIsNotNull(
      @Param("tenantId") @NotNull String tenantId);

  /**
   * Same as {@link #findAllByTenantIdAndSecurityPlatformIsNotNull}, without the collectors whose
   * connector instance in the tenant (configuration {@code idKey}) is stopped or stopping.
   */
  @Query(
      nativeQuery = true,
      value =
          """
              SELECT c.* FROM collectors c
              WHERE c.tenant_id = :tenantId
              AND c.collector_security_platform IS NOT NULL
              AND NOT EXISTS (
                SELECT 1
                FROM connector_instance_configurations conf
                JOIN connector_instances instance ON conf.connector_instance_id = instance.connector_instance_id
                WHERE conf.connector_instance_configuration_key = :idKey
                AND jsonb_exists(conf.connector_instance_configuration_value, c.collector_id)
                AND instance.tenant_id = c.tenant_id
                AND (instance.connector_instance_current_status = 'stopped'
                  OR instance.connector_instance_requested_status = 'stopping'))
              """)
  List<Collector> findAllActiveByTenantIdAndSecurityPlatformIsNotNull(
      @Param("tenantId") @NotNull String tenantId, @Param("idKey") @NotNull String idKey);
}
