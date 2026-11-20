package io.openaev.database.repository;

import io.openaev.database.model.scenario.ScenarioIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScenarioIdentityRepository extends JpaRepository<ScenarioIdentity, String> {

  Optional<ScenarioIdentity> findByTenantIdAndRepositoryAndExternalId(
      String tenantId, String repository, String externalId);

  List<ScenarioIdentity> findAllByTenantId(String tenantId);

  @Query(
      "SELECT i FROM ScenarioIdentity i WHERE i.tenant.id = :tenantId"
          + " AND EXISTS (SELECT s FROM Scenario s WHERE s.catalogIdentity = i)")
  List<ScenarioIdentity> findAllInstalledByTenantId(@Param("tenantId") String tenantId);
}
