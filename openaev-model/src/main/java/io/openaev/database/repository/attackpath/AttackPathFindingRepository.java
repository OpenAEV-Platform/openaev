package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingVerdictRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingListRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.model.attackpath.projection.AttackPathTypeCountRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
   * Delta read (#6647, spec 002): the same projection as {@link #findGraphRows}, restricted to the
   * findings written since {@code since}. Backed by {@code idx_ap_finding_sim_rowversion}. A
   * finding whose value was re-discovered is re-stamped by the copy's conflict branch, so it comes
   * back here with its (possibly new) links; one left untouched keeps its original version and is
   * correctly absent.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathFindingRow("
          + "f.id, f.type, f.value, f.endpointId, f.endpointRaw, f.endpointKey, ef.executionId) "
          + "FROM AttackPathFinding f "
          + "JOIN AttackPathExecutionFinding ef ON ef.findingId = f.id "
          + "WHERE f.simulationId = :simulationId AND f.rowVersion > :since")
  List<AttackPathFindingRow> findGraphRowsSince(
      @Param("simulationId") String simulationId, @Param("since") long since);

  /**
   * How many rows {@link #findGraphRowsSince} would return, for the delta's resync threshold.
   * Counted over the SAME link join, not over the findings alone: a finding produced by several
   * executions yields one row per producer, so counting bare findings would under-report the
   * payload this guard exists to bound. Counted rather than fetched, so the guard stays cheaper
   * than the work it avoids.
   */
  @Query(
      "SELECT count(ef) FROM AttackPathFinding f "
          + "JOIN AttackPathExecutionFinding ef ON ef.findingId = f.id "
          + "WHERE f.simulationId = :simulationId AND f.rowVersion > :since")
  long countChangedSince(@Param("simulationId") String simulationId, @Param("since") long since);

  /**
   * Delta read: {@link #findEndpointTypeCounts} restricted to the endpoints a delta actually
   * touched, so each affected endpoint node ships its full recomputed per-type finding counts (FR1:
   * aggregates whole, never as increments) at O(changed endpoints).
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow("
          + "f.endpointKey, f.type, count(distinct f.value)) "
          + "FROM AttackPathFinding f WHERE f.simulationId = :simulationId "
          + "AND f.endpointKey IN :endpointKeys "
          + "AND EXISTS (SELECT ef FROM AttackPathExecutionFinding ef WHERE ef.findingId = f.id) "
          + "GROUP BY f.endpointKey, f.type")
  List<AttackPathEndpointTypeCountRow> findEndpointTypeCountsByEndpointKeys(
      @Param("simulationId") String simulationId,
      @Param("endpointKeys") Collection<String> endpointKeys);

  /**
   * Expand one endpoint: its findings' (type, value) joined to their producing executions' three
   * status columns, so the read can carry a per-finding verdict. The inner join to {@code
   * AttackPathExecutionFinding} is the graph invariant (a finding is in the graph iff an execution
   * produced it) and replaces the former {@code EXISTS} semi-join; it multiplies rows per producer,
   * which the service groups per (type, value) and worst-of aggregates. One indexed read using
   * {@code idx_ap_find_sim_endpointkey_type}; the {@code AttackPathFinding} scope is the tenant
   * fail-closed boundary; {@code endpointKey} is the asset id or the raw value. The joined
   * execution is pinned to the same simulation, so a corrupt cross-simulation link cannot attach
   * another run's status.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingVerdictRow("
          + "f.type, f.value, e.preventionStatus, e.detectionStatus, e.vulnerabilityStatus) "
          + "FROM AttackPathFinding f "
          + "JOIN AttackPathExecutionFinding ef ON ef.findingId = f.id "
          + "JOIN AttackPathExecution e ON e.id = ef.executionId "
          + "WHERE f.simulationId = :simulationId AND f.endpointKey = :endpointKey "
          + "AND e.simulationId = :simulationId")
  List<AttackPathEndpointFindingVerdictRow> findByEndpoint(
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

  /**
   * Widget drawer (issue 5048): a page of a simulation's findings of the given types, restricted to
   * findings a producing execution links to (the same {@code EXISTS} invariant as the graph reads,
   * so a drawer never lists a finding the graph omits). The explicit {@code countQuery} keeps the
   * paged total consistent with that invariant; the tenant filter is added by the statement
   * inspector. A stable {@code ORDER BY (endpointKey, value, id)} makes paging deterministic, so
   * scrolling the drawer never repeats or skips a row ({@code id} is the unique tiebreaker).
   */
  @Query(
      value =
          "SELECT new io.openaev.database.model.attackpath.projection.AttackPathFindingListRow("
              + "f.id, f.type, f.value, f.endpointKey) "
              + "FROM AttackPathFinding f "
              + "WHERE f.simulationId = :simulationId AND f.type IN :types "
              + "AND EXISTS (SELECT ef FROM AttackPathExecutionFinding ef WHERE ef.findingId = f.id) "
              + "ORDER BY f.endpointKey, f.value, f.id",
      countQuery =
          "SELECT count(f) FROM AttackPathFinding f "
              + "WHERE f.simulationId = :simulationId AND f.type IN :types "
              + "AND EXISTS (SELECT ef FROM AttackPathExecutionFinding ef WHERE ef.findingId = f.id)")
  Page<AttackPathFindingListRow> findPageByTypes(
      @Param("simulationId") String simulationId,
      @Param("types") Collection<String> types,
      Pageable pageable);

  /**
   * Widget drawer (issue 5048): the (finding, producing execution) links for a page of findings, so
   * each drawer row can carry its execution ids for cross-focus.
   *
   * <p>The join on {@code AttackPathFinding} is what makes this read tenant-safe, and it is not
   * decorative. {@code attackpath_execution_finding} has no {@code tenant_id} of its own, so it is
   * deliberately not tenant-active and the statement inspector adds no predicate to it. Read alone
   * it would return every tenant's links. Joined to its guarded parent, it inherits the parent's
   * scope. Isolation here is enforced by the mechanism rather than argued from the caller passing
   * ids it obtained from a scoped page. Pinned by {@code linkReadWithoutScopeIsFailClosed}. The
   * joined execution is pinned to the finding's simulation, so a corrupt cross-simulation link
   * cannot attach another run's status.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathFindingExecutionRow("
          + "ef.findingId, ef.executionId, e.preventionStatus, e.detectionStatus,"
          + " e.vulnerabilityStatus) "
          + "FROM AttackPathExecutionFinding ef "
          + "JOIN AttackPathFinding f ON f.id = ef.findingId "
          + "JOIN AttackPathExecution e ON e.id = ef.executionId "
          + "WHERE ef.findingId IN :findingIds "
          + "AND e.simulationId = f.simulationId "
          + "ORDER BY ef.executionId")
  List<AttackPathFindingExecutionRow> findExecutionLinks(
      @Param("findingIds") Collection<String> findingIds);

  /**
   * Result tab (issue 5048): the findings one execution produced (its (type, value)), via the
   * execution-finding link. The link is the producing relation, so no {@code EXISTS} is needed.
   * Used to list an execution's findings and to mask its credential secrets in the terminal.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingRow("
          + "f.type, f.value) "
          + "FROM AttackPathFinding f "
          + "JOIN AttackPathExecutionFinding ef ON ef.findingId = f.id "
          + "WHERE ef.executionId = :executionId "
          + "ORDER BY f.type, f.value")
  List<AttackPathEndpointFindingRow> findByExecutionId(@Param("executionId") String executionId);

  /**
   * Delete every finding of a simulation. Through Hibernate so the tenant inspector scopes it; the
   * {@code attackpath_execution_finding} links ride the ON DELETE CASCADE on {@code finding_id}.
   * Used by the attack-path cleanup on simulation reset and delete.
   */
  void deleteAllBySimulationId(String simulationId);
}
