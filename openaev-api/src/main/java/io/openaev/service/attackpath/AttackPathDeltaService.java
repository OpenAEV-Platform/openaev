package io.openaev.service.attackpath;

import io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointTypeCountRow;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathDeltaDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
 */
@Service
@RequiredArgsConstructor
public class AttackPathDeltaService {

  /**
   * Beyond this many changed rows, answering with a resync is cheaper than assembling a
   * snapshot-sized delta — and the client would be re-laying-out the whole graph anyway. This is
   * the bound that keeps a far-behind cursor from turning one poll into a full rebuild (FR17).
   */
  private static final long MAX_DELTA_ROWS = 5_000;

  private final AttackPathGraphService graphService;
  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathFindingRepository findingRepository;
  private final AttackPathVersionService versionService;

  @Transactional(readOnly = true)
  public AttackPathDeltaDTO buildDelta(String simulationId, long since) {
    Optional<Long> current = versionService.current(simulationId);
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
    long changed =
        executionRepository.countChangedSince(simulationId, since)
            + findingRepository.countChangedSince(simulationId, since);
    if (changed > MAX_DELTA_ROWS) {
      return AttackPathDeltaDTO.resync(since, currentVersion);
    }
    return assembleDelta(simulationId, since, currentVersion);
  }

  private AttackPathDeltaDTO assembleDelta(String simulationId, long since, long currentVersion) {
    List<AttackPathExecutionRow> executions =
        executionRepository.findGraphRowsSince(simulationId, since);
    List<AttackPathFindingRow> findings = findingRepository.findGraphRowsSince(simulationId, since);

    AttackPathDTO partial = graphService.assemble(executions, findings);
    Map<String, AttackPathNodeDTO> nodesById = new LinkedHashMap<>();
    partial.attackPathNodes().forEach(node -> nodesById.put(node.getId(), node));
    Map<String, AttackPathEdges> edgesById = new LinkedHashMap<>();
    partial.attackPathEdges().forEach(edge -> edgesById.put(edge.getEdgeId(), edge));

    Set<String> affectedEndpoints = new LinkedHashSet<>();
    executions.forEach(e -> affectedEndpoints.add(e.targetKey()));
    findings.forEach(f -> affectedEndpoints.add(f.endpointKey()));
    recomputeAggregates(simulationId, affectedEndpoints, nodesById, edgesById);

    return new AttackPathDeltaDTO(
        since,
        currentVersion,
        false,
        partial.staticAttackPathFindings(),
        partial.attackPathExecutions(),
        new ArrayList<>(nodesById.values()),
        new ArrayList<>(edgesById.values()),
        graphService.collapsedCounters(
            executionRepository.countEndpoints(simulationId),
            findingRepository.findTypeCounts(simulationId)));
  }

  /**
   * Replaces, on the endpoints this delta touched, every value the partial rebuild could only have
   * derived from a subset: the endpoint's worst-case colour, its per-type finding counts, and each
   * incoming edge's execution count. Without this an endpoint that gained one prevented execution
   * would flip to GREEN because the delta only saw that one row.
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
