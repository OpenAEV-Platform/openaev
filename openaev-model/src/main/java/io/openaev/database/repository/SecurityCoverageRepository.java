package io.openaev.database.repository;

import io.openaev.database.model.SecurityCoverage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityCoverageRepository
    extends CrudRepository<SecurityCoverage, String>, JpaSpecificationExecutor<SecurityCoverage> {

  Optional<SecurityCoverage> findByExternalId(String id);

  // Defensive variant: duplicates are prevented by the unique constraint on
  // security_coverage_external_id, but a legacy-duplicated database must never fail an entire
  // bundle with a NonUniqueResultException. The caller picks the best row deterministically.
  List<SecurityCoverage> findAllByExternalId(String id);
}
