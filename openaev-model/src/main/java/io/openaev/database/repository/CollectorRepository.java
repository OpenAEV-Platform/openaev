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

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM collectors WHERE collector_id = :id AND tenant_id = :tenantId")
  void deleteByIdAndTenantId(@Param("id") String id, @Param("tenantId") String tenantId);

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
