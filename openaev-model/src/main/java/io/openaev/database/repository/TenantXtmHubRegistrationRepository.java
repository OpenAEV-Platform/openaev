package io.openaev.database.repository;

import io.openaev.database.model.TenantXtmHubRegistration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TenantXtmHubRegistrationRepository
    extends JpaRepository<TenantXtmHubRegistration, String>,
        JpaSpecificationExecutor<TenantXtmHubRegistration> {

  Optional<TenantXtmHubRegistration> findByTenantId(String tenantId);

  @Transactional
  void deleteByTenantId(String tenantId);
}
