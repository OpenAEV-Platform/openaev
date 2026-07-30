package io.openaev.service.attackpath;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointAgentRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.dto.AttackPathCounters;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathDeltaDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "what changed in this simulation's attack path since version v" (#6647, spec 002).
 *
 * <p>There is no journal and no retention window. Every projection write stamps the simulation's
 * bumped version on the rows it touches, so the delta is a cursor read straight off the projection
 * tables: any {@code since} up to the current version is answerable forever, and the only
 * unanswerable cursors are the ones that describe a state the simulation no longer has (reset, or
 * deleted).
 *
 * <p>The entities are built by handing the CHANGED rows to the snapshot's own rebuild pass ({@link
 * AttackPathGraphService#assemble}). That is deliberate and load-bearing: the delta cannot drift
 * from the snapshot's ids, node shapes or field set, because it is not a second implementation.
 * What a subset of rows genuinely cannot compute — an endpoint's worst-case colour, its per-type
 * finding counts, an edge's execution count — is then recomputed over ALL the rows of the affected
 * endpoints and shipped whole (FR1), which is O(what changed) rather than O(graph).
 *
 * <p>The causal wiring gets the same treatment, in both directions, because the pass derives it by
 * intersecting the executions and the findings it is handed and a bump only ever carries one side:
 * the executions that PRODUCED the batch's findings are pulled in before the pass ({@link
 * #withProducersOf}), and the keys the batch's executions CONSUME are re-resolved after it against
 * every finding they could match ({@link AttackPathGraphService#recomputeEventDependencies}).
 */
@Service
@RequiredArgsConstructor
public class AttackPathDeltaService {

  /**
   * Beyond this many changed rows, answering with a resync is cheaper than assembling a
   * snapshot-sized delta — and the client would be re-laying-out the whole graph anyway. This is
   * the bound that keeps a far-behind cursor from turning one poll into a full rebuild (FR17).
   */
  @Value("${openaev.attackpath.delta-max-rows:5000}")
  private long maxDeltaRows;

  private final AttackPathGraphService graphService;
  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathFindingRepository findingRepository;
  private final AttackPathVersionService versionService;

  /**
   * The counters of one (simulation, version) tick, computed once however many clients poll for it.
   * The two aggregate queries behind them are O(graph), not O(changed), so with N viewers on a
   * running simulation the naive cost is N aggregations per write — the one part of a delta that
   * does not shrink with the change. The key includes the version, so a new write always
   * recomputes; the entries are only worth keeping for as long as a poll cycle, hence the short
   * expiry.
   */
  private final Cache<String, AttackPathCounters> countersCache =
      Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofMinutes(1)).build();

  /**
   * @param tenantIds the request's tenant scope, for the version counter alone: unlike the
   *     projection tables it is not tenant-active, so its reads carry the scope explicitly (see
   *     {@link AttackPathTenantScope}).
   */
  @Transactional(readOnly = true)
  public AttackPathDeltaDTO buildDelta(
      String simulationId, long since, Collection<String> tenantIds) {
    Optional<Long> current = versionService.current(simulationId, tenantIds);
    if (current.isEmpty()) {
      // No counter: either the simulation never produced attack-path data, or its data was deleted.
      // A client claiming a version has therefore lost the state it describes and must resync; a
      // client at 0 has nothing to catch up on.
      return since == 0 ? AttackPathDeltaDTO.empty(0, 0) : AttackPathDeltaDTO.resync(since, 0);
    }
    long currentVersion = current.get();
    if (since > currentVersion) {
      return AttackPathDeltaDTO.resync(
          since, currentVersion); // reset and re-seeded under the client
    }
    if (since == currentVersion) {
      return AttackPathDeltaDTO.empty(
          since, currentVersion); // steady state: one point read, no more
    }
    // Two counts, the second only when the first has not already blown the budget: over the
    // threshold the answer is a resync whatever the exact total is.
    long changed = executionRepository.countChangedSince(simulationId, since);
    if (changed <= maxDeltaRows) {
      changed += findingRepository.countChangedSince(simulationId, since);
    }
    if (changed > maxDeltaRows) {
      return AttackPathDeltaDTO.resync(since, currentVersion);
    }
    return assembleDelta(simulationId, since, currentVersion, tenantIds);
  }

  private AttackPathDeltaDTO assembleDelta(
      String simulationId, long since, long currentVersion, Collection<String> tenantIds) {
    List<AttackPathExecutionRow> changedExecutions =
        executionRepository.findGraphRowsSince(simulationId, since);
    List<AttackPathFindingRow> findings = findingRepository.findGraphRowsSince(simulationId, since);
    if (changedExecutions.isEmpty() && findings.isEmpty()) {
      // The version moved but nothing this read can see changed (a guarded verdict update that
      // matched no row, or a re-copy of identical findings): an empty tick, and null counters say
      // "keep the ones you have" rather than paying two aggregate queries to confirm them.
      return AttackPathDeltaDTO.empty(since, currentVersion);
    }
    List<AttackPathExecutionRow> executions =
        withProducersOf(simulationId, changedExecutions, findings);

    AttackPathDTO partial = graphService.assemble(executions, findings, 0);
    Map<String, AttackPathNodeDTO> nodesById = new LinkedHashMap<>();
    partial.attackPathNodes().forEach(node -> nodesById.put(node.getId(), node));
    Map<String, AttackPathEdges> edgesById = new LinkedHashMap<>();
    partial.attackPathEdges().forEach(edge -> edgesById.put(edge.getEdgeId(), edge));

    Set<String> affectedEndpoints = new LinkedHashSet<>();
    executions.forEach(e -> affectedEndpoints.add(e.targetKey()));
    findings.forEach(f -> affectedEndpoints.add(f.endpointKey()));
    recomputeAggregates(simulationId, affectedEndpoints, nodesById, edgesById);
    // The other half of the causal closure: a consuming step's keys matched against every finding
    // they could reach, not just this batch's — the findings a key consumes were written before it.
    graphService.recomputeEventDependencies(
        simulationId, executions, partial.attackPathExecutions());

    return new AttackPathDeltaDTO(
        since,
        currentVersion,
        false,
        partial.staticAttackPathFindings(),
        partial.attackPathExecutions(),
        new ArrayList<>(nodesById.values()),
        new ArrayList<>(edgesById.values()),
        counters(simulationId, currentVersion, tenantIds));
  }

  /**
   * Adds the executions that produced this batch's findings, when they are not in the batch
   * already.
   *
   * <p>This is what makes the delta causally closed, and it is not an optimisation detail: findings
   * are copied in their OWN version bump, after the execution that produced them, so a batch that
   * carries findings almost never carries their producers. The rebuild pass derives the causal
   * wiring — an execution's {@code findingsNodeIds}, a finding node's worst-case verdict, the event
   * dependencies between steps — by intersecting the executions and the findings it is handed, so
   * without the producers it emits finding nodes that no execution claims. The causal chain places
   * a finding only from its producer's list, so the client accumulated the nodes and rendered none
   * of them: the whole finding layer of the graph appeared only after a reload, which re-read the
   * snapshot over all the rows at once.
   *
   * <p>One primary-key read, bounded by the batch's distinct producers.
   */
  private List<AttackPathExecutionRow> withProducersOf(
      String simulationId,
      List<AttackPathExecutionRow> executions,
      List<AttackPathFindingRow> findings) {
    Set<String> present = new HashSet<>();
    executions.forEach(e -> present.add(e.id()));
    Set<String> missing = new LinkedHashSet<>();
    for (AttackPathFindingRow f : findings) {
      if (f.executionId() != null && !present.contains(f.executionId())) {
        missing.add(f.executionId());
      }
    }
    if (missing.isEmpty()) {
      return executions;
    }
    List<AttackPathExecutionRow> closed = new ArrayList<>(executions);
    closed.addAll(executionRepository.findGraphRowsByIds(simulationId, missing));
    return closed;
  }

  /**
   * The top-bar counters of this tick, computed once per (simulation, version) rather than once per
   * poll. They are the only part of a delta that is O(graph): both queries aggregate the whole
   * projection, so N viewers of a running simulation would otherwise pay N aggregations for every
   * write. Shipped whole, never as increments (FR1).
   *
   * <p>The tenant scope belongs in the key, not only the simulation and version: the counts are
   * inspector-filtered per scope, so keying on the simulation alone would serve one scope's counts
   * to another.
   */
  private AttackPathCounters counters(
      String simulationId, long version, Collection<String> tenantIds) {
    return countersCache.get(
        simulationId + ' ' + version + ' ' + String.join(",", tenantIds),
        key ->
            graphService.collapsedCounters(
                executionRepository.countEndpoints(simulationId),
                findingRepository.findTypeCounts(simulationId)));
  }

  /**
   * Replaces, on the endpoints this delta touched, every value the partial rebuild could only have
   * derived from a subset: the endpoint's worst-case colour, its per-type finding counts, its agent
   * list, and each incoming edge's execution count. Without this an endpoint that gained one
   * prevented execution would flip to GREEN, and one that gained an execution from a second agent
   * would shrink to that agent alone, because the delta only saw that one row.
   */
  private void recomputeAggregates(
      String simulationId,
      Set<String> affectedEndpoints,
      Map<String, AttackPathNodeDTO> nodesById,
      Map<String, AttackPathEdges> edgesById) {
    if (affectedEndpoints.isEmpty()) {
      return;
    }
    for (AttackPathEndpointGroupRow row :
        executionRepository.findEndpointGroupsByTargetKeys(simulationId, affectedEndpoints)) {
      AttackPathNodeDTO node = nodesById.get(AttackPathIds.endpointNode(row.targetKey()));
      if (node != null) {
        node.setStatus(graphService.collapsedColour(row.redCount(), row.orangeCount()));
      }
    }

    Map<String, Map<String, Long>> countsByEndpoint = new LinkedHashMap<>();
    for (AttackPathEndpointTypeCountRow row :
        findingRepository.findEndpointTypeCountsByEndpointKeys(simulationId, affectedEndpoints)) {
      countsByEndpoint
          .computeIfAbsent(row.endpointKey(), key -> new LinkedHashMap<>())
          .put(row.type(), row.distinctValues());
    }
    // Every affected endpoint, not only those with counts: an endpoint whose last finding was
    // removed must be told its counts are now empty, and a missing key would read as "unchanged".
    for (String endpointKey : affectedEndpoints) {
      AttackPathNodeDTO node = nodesById.get(AttackPathIds.endpointNode(endpointKey));
      if (node != null) {
        node.setFindingCounts(countsByEndpoint.getOrDefault(endpointKey, Map.of()));
      }
    }

    Map<String, List<String>> agentsByEndpoint = new LinkedHashMap<>();
    for (AttackPathEndpointAgentRow row :
        executionRepository.findEndpointAgentsByTargetKeys(simulationId, affectedEndpoints)) {
      agentsByEndpoint
          .computeIfAbsent(row.targetKey(), key -> new ArrayList<>())
          .add(row.agentName());
    }
    // Same reasoning as the finding counts: every affected endpoint, so an endpoint that no longer
    // has a named agent is told so rather than keeping the list a client already rendered.
    for (String endpointKey : affectedEndpoints) {
      AttackPathNodeDTO node = nodesById.get(AttackPathIds.endpointNode(endpointKey));
      if (node != null) {
        node.setAgents(agentsByEndpoint.getOrDefault(endpointKey, List.of()));
      }
    }

    for (AttackPathEdgeGroupRow group :
        executionRepository.findEdgeGroupsByTargetKeys(simulationId, affectedEndpoints)) {
      String edgeId =
          AttackPathIds.executionsEdge(
              graphService.collapsedSourceNodeId(group),
              AttackPathIds.endpointNode(group.targetKey()));
      AttackPathEdges edge = edgesById.get(edgeId);
      if (edge != null) {
        edge.setCount((int) group.count());
      }
    }
  }
}
