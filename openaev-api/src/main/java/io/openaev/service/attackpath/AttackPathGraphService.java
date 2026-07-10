package io.openaev.service.attackpath;

import io.openaev.database.model.attackpath.projection.AttackPathEndpointFindingRow;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.dto.AttackPathCounters;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathEndpointRelationsDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds a simulation's attack-path graph (issue 6647). The whole graph comes from exactly two
 * flat, indexed reads (Read A: executions; Read B: findings joined to their producing execution)
 * plus one in-memory pass that turns the rows into {@code {nodes, edges, counters}} with the
 * deterministic IDs from {@link AttackPathIds}. No recursion, and the number of SQL statements is a
 * constant two, independent of the graph size. Each read is walked exactly once (counters are
 * accumulated inside the findings pass).
 *
 * <p>The execution is carried on the source-to-target edge (its {@code executionIds}), not as a
 * standalone map node (design O2), while the left feed still lists every execution.
 */
@Service
@RequiredArgsConstructor
public class AttackPathGraphService {

  private static final String SOURCE_INJECTOR = "INJECTOR";
  private static final String PREVENTED = "Prevented";

  private static final String TYPE_INJECTOR = "INJECTOR";
  private static final String TYPE_ASSET = "ASSET";
  private static final String TYPE_EXECUTION = "EXECUTION";
  private static final String TYPE_FINDING_TYPE = "FINDING_TYPE";
  private static final String TYPE_FINDING = "FINDING";

  private static final String EDGE_EXECUTIONS = "EDGE_EXECUTIONS";
  private static final String EDGE_ENDPOINT_FINDINGS_TYPE = "EDGE_ENDPOINT_FINDINGS_TYPE";
  private static final String EDGE_FINDINGS_TYPE_FINDING = "EDGE_FINDINGS_TYPE_FINDING";

  private static final String GREEN = "GREEN";
  private static final String ORANGE = "ORANGE";
  private static final String RED = "RED";

  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathFindingRepository findingRepository;

  @Transactional(readOnly = true)
  public AttackPathDTO buildGraph(String simulationId) {
    List<AttackPathExecutionRow> executions = executionRepository.findGraphRows(simulationId);
    List<AttackPathFindingRow> findings = findingRepository.findGraphRows(simulationId);
    return assemble(executions, findings);
  }

  /**
   * Expand one endpoint into its finding-type nodes and finding nodes, from a single indexed read.
   * {@code endpointKey} is the asset id or the raw value of a discovered endpoint.
   */
  @Transactional(readOnly = true)
  public AttackPathExpandDTO expandEndpoint(String simulationId, String endpointKey) {
    List<AttackPathEndpointFindingRow> findings =
        findingRepository.findByEndpoint(simulationId, endpointKey);
    String assetNodeId = AttackPathIds.endpointNode(endpointKey);
    Map<String, AttackPathNodeDTO> typeNodes = new LinkedHashMap<>();
    Map<String, AttackPathNodeDTO> findingNodes = new LinkedHashMap<>();
    for (AttackPathEndpointFindingRow f : findings) {
      String typeNodeId = AttackPathIds.findingTypeNode(f.type(), endpointKey);
      typeNodes.computeIfAbsent(typeNodeId, id -> findingTypeNode(id, f.type(), assetNodeId));
      String findingNodeId = AttackPathIds.findingNode(f.type(), f.value());
      findingNodes.computeIfAbsent(
          findingNodeId, id -> findingNode(id, f.type(), f.value(), typeNodeId, assetNodeId));
    }
    return new AttackPathExpandDTO(
        new ArrayList<>(typeNodes.values()), new ArrayList<>(findingNodes.values()));
  }

  /**
   * An endpoint's relations: the executions targeting it (as feed nodes) and the grouped edges into
   * it, from a single indexed read. {@code targetKey} is the asset id or the raw value.
   */
  @Transactional(readOnly = true)
  public AttackPathEndpointRelationsDTO endpointRelations(String simulationId, String targetKey) {
    List<AttackPathExecutionRow> executions =
        executionRepository.findByTarget(simulationId, targetKey);
    String targetNodeId = AttackPathIds.endpointNode(targetKey);
    Map<String, AttackPathEdges> edges = new LinkedHashMap<>();
    List<AttackPathNodeDTO> feed = new ArrayList<>();
    for (AttackPathExecutionRow e : executions) {
      String sourceNodeId = sourceNodeId(e);
      String edgeId = AttackPathIds.executionsEdge(sourceNodeId, targetNodeId);
      AttackPathEdges edge =
          edges.computeIfAbsent(edgeId, id -> executionEdge(id, sourceNodeId, targetNodeId));
      edge.setCount(edge.getCount() + 1);
      edge.getExecutionIds().add(e.id());
      feed.add(executionFeedNode(e));
    }
    return new AttackPathEndpointRelationsDTO(feed, new ArrayList<>(edges.values()));
  }

  private AttackPathDTO assemble(
      List<AttackPathExecutionRow> executions, List<AttackPathFindingRow> findings) {
    Map<String, AttackPathNodeDTO> nodes = new LinkedHashMap<>();
    Map<String, AttackPathEdges> edges = new LinkedHashMap<>();
    Map<String, AttackPathNodeDTO> feedByExecutionId = new LinkedHashMap<>();
    Map<String, List<AttackPathExecutionRow>> byTarget = new LinkedHashMap<>();

    // Pass over executions: injector/source nodes, grouped execution edges, feed entries.
    for (AttackPathExecutionRow e : executions) {
      byTarget.computeIfAbsent(e.targetKey(), k -> new ArrayList<>()).add(e);

      String sourceNodeId = sourceNode(e, nodes);
      String targetNodeId = AttackPathIds.endpointNode(e.targetKey());

      String edgeId = AttackPathIds.executionsEdge(sourceNodeId, targetNodeId);
      AttackPathEdges edge =
          edges.computeIfAbsent(edgeId, id -> executionEdge(id, sourceNodeId, targetNodeId));
      edge.setCount(edge.getCount() + 1);
      edge.getExecutionIds().add(e.id());

      feedByExecutionId.put(e.id(), executionFeedNode(e));
    }

    // Endpoint (ASSET) nodes, with attributes and colour from the executions targeting them.
    for (Map.Entry<String, List<AttackPathExecutionRow>> entry : byTarget.entrySet()) {
      String nodeId = AttackPathIds.endpointNode(entry.getKey());
      nodes.put(nodeId, assetNode(nodeId, entry.getKey(), entry.getValue()));
    }

    // Single pass over findings: finding-type nodes, finding nodes (deduped by type+value), finding
    // edges, the execution -> finding-node cross-reference (US6), and the counters. No extra query,
    // no second walk of the findings.
    List<AttackPathNodeDTO> staticFindings = new ArrayList<>();
    Set<String> seenFindingNodes = new HashSet<>();
    Map<String, List<String>> findingNodeIdsByExecution = new LinkedHashMap<>();
    Set<String> credentialKeys = new HashSet<>();
    Set<String> userKeys = new HashSet<>();
    Set<String> cveKeys = new HashSet<>();
    Set<String> portKeys = new HashSet<>();
    for (AttackPathFindingRow f : findings) {
      String assetNodeId = AttackPathIds.endpointNode(f.endpointKey());
      String typeNodeId = AttackPathIds.findingTypeNode(f.type(), f.endpointKey());
      nodes.computeIfAbsent(typeNodeId, id -> findingTypeNode(id, f.type(), assetNodeId));

      edges.computeIfAbsent(
          AttackPathIds.endpointFindingTypeEdge(f.type(), f.endpointKey()),
          id -> plainEdge(id, assetNodeId, typeNodeId, EDGE_ENDPOINT_FINDINGS_TYPE));

      String findingNodeId = AttackPathIds.findingNode(f.type(), f.value());
      AttackPathNodeDTO findingNode =
          nodes.computeIfAbsent(
              findingNodeId, id -> findingNode(id, f.type(), f.value(), typeNodeId, assetNodeId));
      if (seenFindingNodes.add(findingNodeId)) {
        staticFindings.add(findingNode);
      }

      edges.computeIfAbsent(
          AttackPathIds.findingTypeFindingEdge(f.type(), f.endpointKey(), f.value()),
          id -> plainEdge(id, typeNodeId, findingNodeId, EDGE_FINDINGS_TYPE_FINDING));

      List<String> produced =
          findingNodeIdsByExecution.computeIfAbsent(f.executionId(), k -> new ArrayList<>());
      if (!produced.contains(findingNodeId)) {
        produced.add(findingNodeId);
      }

      // Counters, distinct by (type, value) so the same value across endpoints counts once
      // (plan §4 / D2). The type is a fixed prefix per category, so "|" cannot cause a collision.
      String counterKey = f.type() + "|" + f.value();
      switch (f.type()) {
        case "credentials" -> credentialKeys.add(counterKey);
        case "username", "admin_username" -> userKeys.add(counterKey);
        case "cve" -> cveKeys.add(counterKey);
        case "port" -> portKeys.add(counterKey);
        default -> {
          // other finding types do not feed a top-bar counter
        }
      }
    }

    // Wire the execution -> findings cross-reference onto the feed nodes.
    findingNodeIdsByExecution.forEach(
        (executionId, findingNodeIds) -> {
          AttackPathNodeDTO feedNode = feedByExecutionId.get(executionId);
          if (feedNode != null) {
            feedNode.setFindingsNodeIds(findingNodeIds);
          }
        });

    AttackPathCounters counters =
        new AttackPathCounters(
            byTarget.size(),
            credentialKeys.size(),
            userKeys.size(),
            cveKeys.size(),
            portKeys.size());
    return new AttackPathDTO(
        staticFindings,
        new ArrayList<>(feedByExecutionId.values()),
        new ArrayList<>(nodes.values()),
        new ArrayList<>(edges.values()),
        counters);
  }

  private String sourceNodeId(AttackPathExecutionRow e) {
    return SOURCE_INJECTOR.equals(e.sourceKind())
        ? AttackPathIds.injectorNode(e.sourceInjector())
        : AttackPathIds.endpointNode(e.sourceAssetId());
  }

  private String sourceNode(AttackPathExecutionRow e, Map<String, AttackPathNodeDTO> nodes) {
    String id = sourceNodeId(e);
    if (SOURCE_INJECTOR.equals(e.sourceKind())) {
      nodes.computeIfAbsent(id, key -> node(key, TYPE_INJECTOR, e.sourceInjector()));
    } else {
      // Agent/asset source: the source endpoint. A placeholder node, overwritten if it is also a
      // target (a pivot chain) by the ASSET pass above.
      nodes.computeIfAbsent(id, key -> node(key, TYPE_ASSET, e.sourceAssetId()));
    }
    return id;
  }

  private AttackPathNodeDTO assetNode(
      String id, String targetKey, List<AttackPathExecutionRow> executions) {
    AttackPathExecutionRow representative =
        executions.stream()
            .max(Comparator.comparing(AttackPathExecutionRow::executedAt, nullsFirst()))
            .orElse(executions.get(0));
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(TYPE_ASSET);
    node.setHostname(representative.targetHostname());
    node.setIp(representative.targetIp());
    node.setPlatform(representative.targetPlatform());
    node.setLabel(
        representative.targetHostname() != null ? representative.targetHostname() : targetKey);
    node.setAgents(
        executions.stream()
            .map(AttackPathExecutionRow::agentName)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
    node.setStatus(endpointColour(executions));
    return node;
  }

  private String endpointColour(List<AttackPathExecutionRow> executions) {
    boolean anyPrevented = false;
    boolean anyNotPrevented = false;
    for (AttackPathExecutionRow e : executions) {
      if (PREVENTED.equals(e.preventionStatus())) {
        anyPrevented = true;
      } else {
        anyNotPrevented = true;
      }
    }
    if (anyPrevented && !anyNotPrevented) {
      return GREEN;
    }
    if (anyNotPrevented && !anyPrevented) {
      return RED;
    }
    return ORANGE;
  }

  private AttackPathNodeDTO executionFeedNode(AttackPathExecutionRow e) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(AttackPathIds.executionNode(e.id(), e.targetKey(), e.agentId()));
    node.setType(TYPE_EXECUTION);
    node.setLabel(e.payloadName());
    node.setStatus(e.preventionStatus());
    node.setPayloadName(e.payloadName());
    node.setExecutedAt(e.executedAt() == null ? null : e.executedAt().toString());
    node.setAgentName(e.agentName());
    node.setPrivilege(e.agentPrivilege());
    node.setStepTemplateId(e.stepTemplateId());
    return node;
  }

  private AttackPathNodeDTO findingTypeNode(String id, String type, String assetNodeId) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(TYPE_FINDING_TYPE);
    node.setLabel(type);
    node.setTypeFindings(type);
    node.setAssetNodeId(assetNodeId);
    return node;
  }

  private AttackPathNodeDTO findingNode(
      String id, String type, String value, String typeNodeId, String assetNodeId) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(TYPE_FINDING);
    node.setLabel(value);
    node.setValue(value);
    node.setTypeFindings(type);
    node.setFindingsTypeNodeId(typeNodeId);
    node.setAssetNodeId(assetNodeId);
    return node;
  }

  private AttackPathNodeDTO node(String id, String type, String label) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setId(id);
    node.setType(type);
    node.setLabel(label);
    return node;
  }

  private AttackPathEdges executionEdge(String id, String sourceNodeId, String targetNodeId) {
    AttackPathEdges edge = plainEdge(id, sourceNodeId, targetNodeId, EDGE_EXECUTIONS);
    edge.setCount(0);
    return edge;
  }

  private AttackPathEdges plainEdge(String id, String source, String target, String type) {
    AttackPathEdges edge = new AttackPathEdges();
    edge.setEdgeId(id);
    edge.setEdgeSourceId(source);
    edge.setEdgeTargetId(target);
    edge.setType(type);
    edge.setCount(1);
    return edge;
  }

  private static Comparator<Instant> nullsFirst() {
    return Comparator.nullsFirst(Comparator.naturalOrder());
  }
}
