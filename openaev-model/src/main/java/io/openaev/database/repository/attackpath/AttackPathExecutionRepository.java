package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointAgentRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathInjectorMetaRow;
import io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AttackPathExecutionRepository extends CrudRepository<AttackPathExecution, String> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "UPDATE AttackPathExecution e "
          + "SET e.terminalOutput = :terminalOutput "
          + "WHERE e.id =:id AND e.tenant.id = :tenantId")
  int updateTerminalViewByExecutionIndex(
      @Param("id") String id,
      @Param("terminalOutput") String terminalOutput,
      @Param("tenantId") String tenantId);

  /**
   * The single write path for expectation verdicts on the projection (#6647, spec 002, FR5). One
   * statement per execution row, resolved by the row's own deterministic id, writing all three
   * status columns together.
   *
   * <p>{@code e.rowVersion = :version} stamps the simulation's freshly bumped attack-path version
   * on every row touched, and that stamp is what makes a verdict reach the delta read: a verdict
   * written without it would sit in the projection unseen until some unrelated write happened to
   * bump past it, which is exactly the frozen-at-pending failure this feature exists to remove.
   *
   * <p>The trailing guard is what makes a replayed step event a no-op. Execution events are
   * replayed by design, so an identical result must match zero rows — neither re-stamping a version
   * nor telling any client something changed. The comparison is per column and OR-ed, so a partial
   * change (a detection verdict landing after a prevention one) still updates, while an identical
   * replay does not. {@code coalesce(..., '')} keeps it null-safe in both directions: a null status
   * is a real value here (pending), and {@code <>} against a null parameter would be UNKNOWN, never
   * true.
   *
   * <p>{@code e.tenant.id = :tenantId} is explicit because this runs off the request thread, under
   * a background tenant scope.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "UPDATE AttackPathExecution e "
          + "SET e.preventionStatus = :preventionStatus "
          + ", e.detectionStatus = :detectionStatus "
          + ", e.vulnerabilityStatus = :vulnerabilityStatus "
          + ", e.rowVersion = :version "
          + "WHERE e.id =:id AND e.tenant.id = :tenantId "
          + "AND (coalesce(e.preventionStatus, '') <> coalesce(:preventionStatus, '') "
          + "OR coalesce(e.detectionStatus, '') <> coalesce(:detectionStatus, '') "
          + "OR coalesce(e.vulnerabilityStatus, '') <> coalesce(:vulnerabilityStatus, ''))")
  int updateExpectationStatusByExecutionId(
      @Param("id") String id,
      @Param("preventionStatus") String preventionStatus,
      @Param("detectionStatus") String detectionStatus,
      @Param("vulnerabilityStatus") String vulnerabilityStatus,
      @Param("tenantId") String tenantId,
      @Param("version") long version);

  /**
   * Result &amp; Terminal drawer (issue 5048): one execution's full row by id, scoped to its
   * simulation. This is the only read that loads the heavy {@code command}/{@code terminal_output}
   * columns; the graph reads never touch them. The tenant filter is added by the statement
   * inspector.
   */
  Optional<AttackPathExecution> findByIdAndSimulationId(String id, String simulationId);

  /**
   * Read A of the rebuild: the edges plus injector/endpoint/execution nodes of a simulation, as a
   * flat projection of the short display columns (never {@code command}/{@code terminal_output}).
   * JPQL, not native; the tenant filter is added by the statement inspector, so the query carries
   * only {@code simulationId}.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathExecutionRow("
          + "e.id, e.sourceKind, e.sourceAssetId, e.agentId, e.agentName, e.agentPrivilege, "
          + "e.sourceInjector, e.targetKind, e.targetAssetId, e.targetRawValue, e.targetKey, "
          + "e.targetHostname, e.targetIp, e.targetPlatform, e.payloadName, e.executedAt, "
          + "e.preventionStatus, e.detectionStatus, e.vulnerabilityStatus, e.stepTemplateId, e.contractExternalId, e.injectorType, e.sourceHostname, e.sourceIp, e.sourcePlatform) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId")
  List<AttackPathExecutionRow> findGraphRows(@Param("simulationId") String simulationId);

  /**
   * Delta read (#6647, spec 002): the same projection as {@link #findGraphRows}, restricted to the
   * rows written since {@code since}. Backed by {@code idx_ap_exec_sim_rowversion}, so the cost is
   * proportional to what changed, not to the graph.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathExecutionRow("
          + "e.id, e.sourceKind, e.sourceAssetId, e.agentId, e.agentName, e.agentPrivilege, "
          + "e.sourceInjector, e.targetKind, e.targetAssetId, e.targetRawValue, e.targetKey, "
          + "e.targetHostname, e.targetIp, e.targetPlatform, e.payloadName, e.executedAt, "
          + "e.preventionStatus, e.detectionStatus, e.vulnerabilityStatus, e.stepTemplateId, e.contractExternalId, e.injectorType, e.sourceHostname, e.sourceIp, e.sourcePlatform) "
          + "FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.rowVersion > :since")
  List<AttackPathExecutionRow> findGraphRowsSince(
      @Param("simulationId") String simulationId, @Param("since") long since);

  /**
   * How many execution rows changed since {@code since}, so an over-far-behind cursor can be
   * answered with a resync instead of a snapshot-sized delta. Counted rather than fetched: the
   * guard must be cheaper than the work it avoids.
   */
  @Query(
      "SELECT count(e) FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.rowVersion > :since")
  long countChangedSince(@Param("simulationId") String simulationId, @Param("since") long since);

  /** Distinct endpoints of a simulation, for the delta's whole-shipped endpoint counter. */
  @Query(
      "SELECT count(distinct e.targetKey) FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId")
  long countEndpoints(@Param("simulationId") String simulationId);

  /**
   * Delta read: {@link #findEndpointGroups} restricted to the endpoints a delta actually touched,
   * so their worst-case colour is recomputed over ALL their executions (a subset of rows would
   * report a colour that flickers as unrelated executions come and go), at O(changed endpoints).
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow("
          + "e.targetKey, max(e.targetAssetId), max(e.targetHostname), max(e.targetIp), "
          + "max(e.targetPlatform), max(e.executedAt), "
          + "sum(case when (e.preventionStatus is null or e.preventionStatus <> 'Prevented') "
          + "and (e.detectionStatus is null or e.detectionStatus <> 'Detected') then 1 else 0 end), "
          + "sum(case when (e.preventionStatus is null or e.preventionStatus <> 'Prevented') "
          + "and e.detectionStatus = 'Detected' then 1 else 0 end)) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId "
          + "AND e.targetKey IN :targetKeys GROUP BY e.targetKey")
  List<AttackPathEndpointGroupRow> findEndpointGroupsByTargetKeys(
      @Param("simulationId") String simulationId,
      @Param("targetKeys") Collection<String> targetKeys);

  /**
   * Delta read: the distinct agent names of the endpoints a delta touched, over ALL their
   * executions. An endpoint node's agent list is a property of the whole endpoint, so a delta that
   * derived it from the changed rows alone would shrink an already-rendered node's list down to the
   * one agent that just ran. Ordered, so the recomputed list is byte-identical to a snapshot's.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointAgentRow("
          + "e.targetKey, e.agentName) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId "
          + "AND e.targetKey IN :targetKeys AND e.agentName IS NOT NULL "
          + "GROUP BY e.targetKey, e.agentName ORDER BY e.targetKey, e.agentName")
  List<AttackPathEndpointAgentRow> findEndpointAgentsByTargetKeys(
      @Param("simulationId") String simulationId,
      @Param("targetKeys") Collection<String> targetKeys);

  /**
   * Delta read: {@link #findEdgeGroups} restricted to the endpoints a delta actually touched, so
   * each affected edge ships its full recomputed execution count rather than an increment.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow("
          + "e.sourceKind, e.sourceInjector, e.sourceAssetId, e.contractExternalId, "
          + "max(e.sourceHostname), max(e.sourceIp), max(e.sourcePlatform), "
          + "e.targetKey, count(e)) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId "
          + "AND e.targetKey IN :targetKeys "
          + "GROUP BY e.sourceKind, e.sourceInjector, e.sourceAssetId, e.contractExternalId, e.targetKey")
  List<AttackPathEdgeGroupRow> findEdgeGroupsByTargetKeys(
      @Param("simulationId") String simulationId,
      @Param("targetKeys") Collection<String> targetKeys);

  /**
   * An endpoint's relations: the executions targeting it. A single indexed read using {@code
   * idx_ap_exec_sim_targetkey}; {@code targetKey} is the asset id or the raw value.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathExecutionRow("
          + "e.id, e.sourceKind, e.sourceAssetId, e.agentId, e.agentName, e.agentPrivilege, "
          + "e.sourceInjector, e.targetKind, e.targetAssetId, e.targetRawValue, e.targetKey, "
          + "e.targetHostname, e.targetIp, e.targetPlatform, e.payloadName, e.executedAt, "
          + "e.preventionStatus, e.detectionStatus, e.vulnerabilityStatus, e.stepTemplateId, e.contractExternalId, e.injectorType, e.sourceHostname, e.sourceIp, e.sourcePlatform) "
          + "FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.targetKey = :targetKey")
  List<AttackPathExecutionRow> findByTarget(
      @Param("simulationId") String simulationId, @Param("targetKey") String targetKey);

  /**
   * One page of an endpoint's executions, for the side panel's feed. Same indexed read as {@link
   * #findByTarget}, ordered so paging is stable while a run keeps inserting: {@code executedAt} is
   * the user-meaningful order and {@code id} breaks its ties (executions of one step share a
   * timestamp), so no row is ever shown twice or skipped between two pages.
   *
   * <p>Only the executions are paged: the caller keeps returning the grouped edges whole, since
   * they are bounded by the endpoint's in-degree rather than by execution count, and they reference
   * execution ids across page boundaries.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathExecutionRow("
          + "e.id, e.sourceKind, e.sourceAssetId, e.agentId, e.agentName, e.agentPrivilege, "
          + "e.sourceInjector, e.targetKind, e.targetAssetId, e.targetRawValue, e.targetKey, "
          + "e.targetHostname, e.targetIp, e.targetPlatform, e.payloadName, e.executedAt, "
          + "e.preventionStatus, e.detectionStatus, e.vulnerabilityStatus, e.stepTemplateId, e.contractExternalId, e.injectorType, e.sourceHostname, e.sourceIp, e.sourcePlatform) "
          + "FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.targetKey = :targetKey "
          + "ORDER BY e.executedAt, e.id")
  List<AttackPathExecutionRow> findPageByTarget(
      @Param("simulationId") String simulationId,
      @Param("targetKey") String targetKey,
      Pageable pageable);

  /** How many executions target one endpoint — the page read's total. */
  @Query(
      "SELECT count(e) FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.targetKey = :targetKey")
  long countByTarget(
      @Param("simulationId") String simulationId, @Param("targetKey") String targetKey);

  /**
   * Number of executions of a simulation, used to pick full vs collapsed mode against a threshold.
   */
  @Query("SELECT count(e) FROM AttackPathExecution e WHERE e.simulationId = :simulationId")
  long countExecutions(@Param("simulationId") String simulationId);

  /**
   * One summary row per simulation (id, distinct endpoints, executions) for the front's picker.
   * {@code GROUP BY} on the tenant-filtered rows, ordered by size so the biggest sims come first.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow("
          + "e.simulationId, count(distinct e.targetKey), count(e)) "
          + "FROM AttackPathExecution e GROUP BY e.simulationId ORDER BY count(e) DESC")
  List<AttackPathSimSummaryRow> findSimulationSummaries();

  /**
   * Same summary, restricted to one scenario's simulations (the picker's scenario context, #6647
   * B0). The subquery maps each {@code simulation_id} back to its {@code exercise} and keeps only
   * those whose scenario is the requested one; ad-hoc simulations (no scenario) and seed rows (no
   * real exercise) are naturally excluded.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow("
          + "e.simulationId, count(distinct e.targetKey), count(e)) "
          + "FROM AttackPathExecution e "
          + "WHERE e.simulationId IN "
          + "(SELECT ex.id FROM Exercise ex WHERE ex.scenario.id = :scenarioId) "
          + "GROUP BY e.simulationId ORDER BY count(e) DESC")
  List<AttackPathSimSummaryRow> findSimulationSummariesByScenario(
      @Param("scenarioId") String scenarioId);

  /**
   * Collapsed mode: one endpoint per {@code target_key}, with a representative of its frozen
   * display attributes ({@code max} per column, which is the constant value within one simulation)
   * and its per-endpoint RED (neither prevented nor detected) and ORANGE (detected but not
   * prevented) execution counts, which {@code collapsedColour} turns into the worst-case status. A
   * {@code GROUP BY}, so the per-execution rows are never materialized; the tenant filter is added
   * by the inspector. The {@code 'Prevented'} / {@code 'Detected'} literals below match {@code
   * ExpectationType.PREVENTION.successLabel} / {@code ExpectationType.DETECTION.successLabel} (they
   * are query-string literals, so they cannot reference the enum directly).
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow("
          + "e.targetKey, max(e.targetAssetId), max(e.targetHostname), max(e.targetIp), "
          + "max(e.targetPlatform), max(e.executedAt), "
          + "sum(case when (e.preventionStatus is null or e.preventionStatus <> 'Prevented') "
          + "and (e.detectionStatus is null or e.detectionStatus <> 'Detected') then 1 else 0 end), "
          + "sum(case when (e.preventionStatus is null or e.preventionStatus <> 'Prevented') "
          + "and e.detectionStatus = 'Detected' then 1 else 0 end)) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId GROUP BY e.targetKey")
  List<AttackPathEndpointGroupRow> findEndpointGroups(@Param("simulationId") String simulationId);

  /**
   * Collapsed mode: one grouped edge per (source, target), with how many executions it groups. A
   * {@code GROUP BY}, so the per-execution rows are never materialized.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow("
          + "e.sourceKind, e.sourceInjector, e.sourceAssetId, e.contractExternalId, "
          + "max(e.sourceHostname), max(e.sourceIp), max(e.sourcePlatform), "
          + "e.targetKey, count(e)) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId "
          + "GROUP BY e.sourceKind, e.sourceInjector, e.sourceAssetId, e.contractExternalId, e.targetKey")
  List<AttackPathEdgeGroupRow> findEdgeGroups(@Param("simulationId") String simulationId);

  /**
   * The distinct injector metadata of a simulation, for the collapsed graph's injector nodes: the
   * injector name with its frozen contract external id and type. One flat distinct read, since the
   * collapsed edges are grouped by source and cannot carry these columns.
   */
  @Query(
      "SELECT DISTINCT new io.openaev.database.model.attackpath.projection.AttackPathInjectorMetaRow("
          + "e.sourceInjector, e.contractExternalId, e.injectorType) "
          + "FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.sourceKind = 'INJECTOR'")
  List<AttackPathInjectorMetaRow> findInjectorMetadata(@Param("simulationId") String simulationId);

  void deleteAllBySimulationId(String simulationId);

  /**
   * The frozen execution rows of one run step, used to attribute a copied finding to its endpoint:
   * each row carries the link key ({@code id}) and the endpoint key ({@code targetKey}). The tenant
   * is passed explicitly so the read is scoped even off the request thread.
   */
  @Query("SELECT e FROM AttackPathExecution e WHERE e.stepId = :stepId AND e.tenant.id = :tenantId")
  List<AttackPathExecution> findByStepIdAndTenantId(
      @Param("stepId") String stepId, @Param("tenantId") String tenantId);
}
