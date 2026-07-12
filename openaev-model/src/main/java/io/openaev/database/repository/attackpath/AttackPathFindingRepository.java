package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.model.attackpath.projection.AttackPathTypeCountRow;
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

  /**
   * Expand one endpoint: its findings' (type, value), restricted to findings a producing execution
   * links to. That {@code EXISTS} semi-join is the graph invariant (a finding is in the graph iff
   * an execution produced it), so expand agrees with the full rebuild (Read B, an inner join) and
   * with the collapsed counters. One indexed read using {@code idx_ap_find_sim_endpointkey_type};
   * {@code endpointKey} is the asset id or the raw value.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingRow("
          + "f.type, f.value) "
          + "FROM AttackPathFinding f "
          + "WHERE f.simulationId = :simulationId AND f.endpointKey = :endpointKey "
          + "AND EXISTS (SELECT ef FROM AttackPathExecutionFinding ef WHERE ef.findingId = f.id)")
  List<AttackPathEndpointFindingRow> findByEndpoint(
      @Param("simulationId") String simulationId, @Param("endpointKey") String endpointKey);

  /**
   * Collapsed mode: the top-bar counters, one row per finding type with its distinct-value count,
   * over findings a producing execution links to (the same {@code EXISTS} invariant as full mode's
   * inner-join read, so the two modes report identical counters). A {@code GROUP BY}, so the
   * per-finding rows are never materialized.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathTypeCountRow("
          + "f.type, count(distinct f.value)) "
          + "FROM AttackPathFinding f WHERE f.simulationId = :simulationId "
          + "AND EXISTS (SELECT ef FROM AttackPathExecutionFinding ef WHERE ef.findingId = f.id) "
          + "GROUP BY f.type")
  List<AttackPathTypeCountRow> findTypeCounts(@Param("simulationId") String simulationId);

  /**
   * Collapsed mode: per-endpoint finding-type counts, to summarise findings on each collapsed
   * endpoint node, over findings a producing execution links to (the same {@code EXISTS} invariant
   * as full mode). A {@code GROUP BY endpoint_key, type}, so the per-finding rows are never
   * materialized.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow("
          + "f.endpointKey, f.type, count(distinct f.value)) "
          + "FROM AttackPathFinding f WHERE f.simulationId = :simulationId "
          + "AND EXISTS (SELECT ef FROM AttackPathExecutionFinding ef WHERE ef.findingId = f.id) "
          + "GROUP BY f.endpointKey, f.type")
  List<AttackPathEndpointTypeCountRow> findEndpointTypeCounts(
      @Param("simulationId") String simulationId);
}
