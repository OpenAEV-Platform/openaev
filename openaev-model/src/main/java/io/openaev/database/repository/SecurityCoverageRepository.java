package io.openaev.database.repository;

import io.openaev.database.model.SecurityCoverage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityCoverageRepository
    extends CrudRepository<SecurityCoverage, String>, JpaSpecificationExecutor<SecurityCoverage> {

  // Defensive variant: duplicates are prevented by the unique constraint on
  // (security_coverage_external_id, tenant_id), but a legacy-duplicated database must never fail
  // an entire bundle with a NonUniqueResultException. The caller picks the best row
  // deterministically.
  List<SecurityCoverage> findAllByExternalIdAndTenantId(String id, String tenantId);
}
