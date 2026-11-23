package io.openaev.database.repository;

import io.openaev.database.model.scenario.ScenarioRevision;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScenarioRevisionRepository extends JpaRepository<ScenarioRevision, String> {

  // The listed columns only: the content of each revision stays unread.
  @Query(
      "SELECT r.id AS id, r.reason AS reason, r.catalogVersion AS catalogVersion,"
          + " u.firstname AS userFirstname, u.lastname AS userLastname, r.createdAt AS createdAt"
          + " FROM ScenarioRevision r LEFT JOIN r.user u"
          + " WHERE r.tenant.id = :tenantId AND r.scenario.id = :scenarioId"
          + " ORDER BY r.createdAt DESC, r.id DESC")
  List<RevisionRow> findRowsByTenantIdAndScenarioId(
      @Param("tenantId") String tenantId, @Param("scenarioId") String scenarioId);

  Optional<ScenarioRevision> findByTenantIdAndScenarioIdAndId(
      String tenantId, String scenarioId, String id);

  boolean existsByTenantIdAndScenarioIdAndReason(
      String tenantId, String scenarioId, ScenarioRevision.Reason reason);

  interface RevisionRow {
    String getId();

    ScenarioRevision.Reason getReason();

    String getCatalogVersion();

    String getUserFirstname();

    String getUserLastname();

    Instant getCreatedAt();
  }
}
