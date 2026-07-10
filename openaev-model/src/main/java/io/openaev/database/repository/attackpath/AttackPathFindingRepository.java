package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface AttackPathFindingRepository extends CrudRepository<AttackPathFinding, String> {

  /**
   * Read B of the rebuild: the finding nodes of a simulation, each joined to the execution that
   * produced it (one row per producing execution). One bounded key-join, no walk; the tenant filter
   * on {@code attackpath_finding} is added by the statement inspector.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathFindingRow("
          + "f.id, f.type, f.value, f.endpointId, f.endpointRaw, f.endpointKey, ef.executionId) "
          + "FROM AttackPathFinding f "
          + "JOIN AttackPathExecutionFinding ef ON ef.findingId = f.id "
          + "WHERE f.simulationId = :simulationId")
  List<AttackPathFindingRow> findGraphRows(@Param("simulationId") String simulationId);
}
