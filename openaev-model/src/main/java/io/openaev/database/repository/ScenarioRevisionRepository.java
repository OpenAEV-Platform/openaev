package io.openaev.database.repository;

import io.openaev.database.model.ScenarioRevision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScenarioRevisionRepository extends JpaRepository<ScenarioRevision, String> {

  List<ScenarioRevision> findAllByScenarioIdOrderByNumberDesc(String scenarioId);

  @Query(
      "SELECT coalesce(max(r.number), 0) FROM ScenarioRevision r WHERE r.scenario.id = :scenarioId")
  int lastNumber(@Param("scenarioId") String scenarioId);
}
