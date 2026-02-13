package io.openaev.database.repository;

import io.openaev.database.model.Tenant;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository
    extends CrudRepository<Tenant, String>, JpaSpecificationExecutor<Tenant> {
  boolean existsByName(String name);

  @Modifying
  @Query(value = "DELETE FROM tenants t WHERE t.tenant_id = :tenantId", nativeQuery = true)
  int deleteByIdNative(@Param("tenantId") String tenantId);
}
