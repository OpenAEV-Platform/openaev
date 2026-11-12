package io.openaev.database.repository;

import io.openaev.database.model.scenario.ScenarioIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScenarioIdentityRepository extends JpaRepository<ScenarioIdentity, String> {

  Optional<ScenarioIdentity> findByTenantIdAndRepositoryAndExternalId(
      String tenantId, String repository, String externalId);

  List<ScenarioIdentity> findAllByTenantId(String tenantId);
}
