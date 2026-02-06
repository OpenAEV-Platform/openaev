package io.openaev.database.repository;

import io.openaev.database.model.Tenant;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository
    extends CrudRepository<Tenant, String>, JpaSpecificationExecutor<Tenant> {
  boolean existsByName(String name);
}
