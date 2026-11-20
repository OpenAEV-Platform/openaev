package io.openaev.database.repository;

import io.openaev.database.model.scenario.ScenarioRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScenarioRevisionRepository extends JpaRepository<ScenarioRevision, String> {

  List<ScenarioRevision> findAllByTenantIdAndScenarioIdOrderByCreatedAtDesc(
      String tenantId, String scenarioId);

  Optional<ScenarioRevision> findByTenantIdAndScenarioIdAndId(
      String tenantId, String scenarioId, String id);
}
