package io.openaev.database.repository;

import io.openaev.database.model.TenantXtmHubRegistration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TenantXtmHubRegistrationRepository
    extends JpaRepository<TenantXtmHubRegistration, String>,
        JpaSpecificationExecutor<TenantXtmHubRegistration> {

  Optional<TenantXtmHubRegistration> findByTenantId(String tenantId);

  @Query("SELECT r FROM TenantXtmHubRegistration r JOIN FETCH r.tenant t WHERE t.deletedAt IS NULL")
  List<TenantXtmHubRegistration> findAllByTenantNotDeleted();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      value = "DELETE FROM tenant_xtmhub_registrations WHERE tenant_id = :tenantId",
      nativeQuery = true)
  void deleteByTenantId(String tenantId);
}
