package io.openaev.api.graph;

import io.openaev.api.graph.dto.GraphEdgeOutput;
import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeOutput;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * The exploration graph of #9388: lazy slices of the asset / asset group / finding / inject /
 * simulation / scenario graph, read on demand, never the whole tenant.
 *
 * <p>Every read carries an explicit {@code tenant.id IN :tenantIds} predicate on each entity it
 * returns, so isolation holds whether the table is v1 ({@code @Filter}) or v2-active (statement
 * inspector), and the caller's {@code TxCtx} scope bounds the tenant set. Every read is capped at
 * {@code limit + 1} rows: the extra row only tells the UI there is more ("load more").
 *
 * <p>Resource RBAC on simulations and scenarios is applied by the caller through {@code
 * visible}: a node the caller may not read is dropped with its edges.
 */
@Service
public class GraphExplorationService {

  /** Hard ceiling of nodes one request can return, whatever the client asks. */
  public static final int MAX_LIMIT = 200;

  /** Hard ceiling of a path search: hops and visited nodes. */
  static final int MAX_PATH_DEPTH = 6;

  static final int MAX_PATH_VISITED = 2_000;

  @PersistenceContext private EntityManager entityManager;

  /** Builder of one slice, deduplicating nodes and edges as rows come in. */
  static final class Slice {
    final Map<String, GraphNodeOutput> nodes = new LinkedHashMap<>();
    final Map<String, GraphEdgeOutput> edges = new LinkedHashMap<>();
    final Map<String, GraphResult> nodeResults = new HashMap<>();
    final Map<String, Long> failed = new HashMap<>();
    final Map<String, Long> findings = new HashMap<>();
    boolean truncated;

    String node(GraphNodeType type, String id, String label, String subtype) {
      String nodeId = GraphNodeOutput.idOf(type, id);
      nodes.putIfAbsent(
          nodeId,
          new GraphNodeOutput(nodeId, type, id, label == null ? id : label, subtype, null, 0, 0));
      return nodeId;
    }

    void edge(GraphEdgeType type, String source, String target, GraphResult result) {
      String id = GraphEdgeOutput.idOf(type, source, target);
      GraphEdgeOutput previous = edges.get(id);
      GraphResult merged = previous == null ? result : GraphResult.worst(previous.result(), result);
      edges.put(id, new GraphEdgeOutput(id, type, source, target, merged));
      if (result == GraphResult.FAILED) {
        failed.merge(source, 1L, Long::sum);
        failed.merge(target, 1L, Long::sum);
      }
      if (result != null) {
        nodeResults.merge(target, result, GraphResult::worst);
        nodeResults.merge(source, result, GraphResult::worst);
      }
      if (type == GraphEdgeType.FOUND_ON) {
        findings.merge(target, 1L, Long::sum);
      }
    }

    GraphOutput build(String focus, Predicate<GraphNodeOutput> visible, List<String> path) {
      List<GraphNodeOutput> kept = new ArrayList<>();
      for (GraphNodeOutput n : nodes.values()) {
        if (!visible.test(n)) {
          continue;
        }
        kept.add(
            new GraphNodeOutput(
                n.nodeId(),
                n.nodeType(),
                n.entityId(),
                n.label(),
                n.subtype(),
                nodeResults.get(n.nodeId()),
                failed.getOrDefault(n.nodeId(), 0L),
                findings.getOrDefault(n.nodeId(), 0L)));
      }
      Set<String> ids = new java.util.HashSet<>();
      kept.forEach(n -> ids.add(n.nodeId()));
      List<GraphEdgeOutput> keptEdges =
          edges.values().stream()
              .filter(e -> ids.contains(e.source()) && ids.contains(e.target()))
              .toList();
      return new GraphOutput(focus, kept, keptEdges, truncated, path);
    }
  }

  // -- Public reads --

  /**
   * The first ring of an entity: every relation of the focus node, each capped at {@code limit}.
   * An empty slice (not an error) when the entity is outside the scope.
   */
  public GraphOutput neighbourhood(
      Collection<String> tenantIds,
      GraphNodeType type,
      String entityId,
      Set<GraphEdgeType> edgeTypes,
      int limit,
      GraphVisibility visibility) {
    int cap = clamp(limit);
    Slice slice = new Slice();
    String focus = expand(slice, tenantIds, type, entityId, edgeTypes, cap);
    Predicate<GraphNodeOutput> visible = gate(tenantIds, visibility);
    if (focus != null && !visible.test(slice.nodes.get(focus))) {
      return new GraphOutput(null, List.of(), List.of(), false, List.of());
    }
    return slice.build(focus, visible, List.of());
  }

  /**
   * The landing view, "exposure hot paths": the assets carrying the most failed expectations, the
   * injects that failed on them, their asset groups and their findings. Risk first, and small.
   */
  public GraphOutput hotPaths(
      Collection<String> tenantIds, int limit, GraphVisibility visibility) {
    int cap = Math.min(clamp(limit), 25);
    Slice slice = new Slice();
    TypedQuery<Object[]> q =
        entityManager.createQuery(
            "SELECT a.id, a.name, a.type, COUNT(e) FROM TechnicalInjectExpectation e"
                + " JOIN e.asset a JOIN e.inject i"
                + " WHERE a.tenant.id IN :tenantIds AND i.tenant.id IN :tenantIds"
                + " AND e.score IS NOT NULL AND e.score < COALESCE(e.expectedScore, 100)"
                + " GROUP BY a.id, a.name, a.type ORDER BY COUNT(e) DESC, a.name ASC",
            Object[].class);
    List<Object[]> rows =
        q.setParameter("tenantIds", tenantIds).setMaxResults(cap + 1).getResultList();
    if (rows.size() > cap) {
      slice.truncated = true;
      rows = rows.subList(0, cap);
    }
    for (Object[] r : rows) {
      String assetId = (String) r[0];
      slice.node(GraphNodeType.ASSET, assetId, (String) r[1], (String) r[2]);
      Set<GraphEdgeType> ring =
          Set.of(GraphEdgeType.MEMBER_OF, GraphEdgeType.FOUND_ON, GraphEdgeType.TARGETS);
      expand(slice, tenantIds, GraphNodeType.ASSET, assetId, ring, 5);
    }
    return slice.build(null, gate(tenantIds, visibility), List.of());
  }

  /**
   * A shortest path between two nodes, breadth-first over the same relations, bounded in depth and
   * in visited nodes. The slice holds every node and edge of the path; an empty path when none is
   * found within the bounds.
   */
  public GraphOutput path(
      Collection<String> tenantIds,
      GraphNodeType fromType,
      String fromId,
      GraphNodeType toType,
      String toId,
      GraphVisibility visibility) {
    Predicate<GraphNodeOutput> visible = gate(tenantIds, visibility);
    String start = GraphNodeOutput.idOf(fromType, fromId);
    String goal = GraphNodeOutput.idOf(toType, toId);
    Map<String, String> parent = new HashMap<>();
    Map<String, GraphEdgeOutput> parentEdge = new HashMap<>();
    Map<String, GraphNodeOutput> seen = new HashMap<>();
    Map<String, Integer> depth = new HashMap<>();
    Deque<GraphNodeOutput> queue = new ArrayDeque<>();
    GraphNodeOutput first =
        new GraphNodeOutput(start, fromType, fromId, fromId, null, null, 0, 0);
    seen.put(start, first);
    depth.put(start, 0);
    queue.add(first);
    boolean found = start.equals(goal);
    while (!queue.isEmpty() && !found && seen.size() < MAX_PATH_VISITED) {
      GraphNodeOutput current = queue.poll();
      if (depth.get(current.nodeId()) >= MAX_PATH_DEPTH) {
        continue;
      }
      Slice ring = new Slice();
      expand(ring, tenantIds, current.nodeType(), current.entityId(), Set.of(), 50);
      for (GraphEdgeOutput e : ring.edges.values()) {
        String other = e.source().equals(current.nodeId()) ? e.target() : e.source();
        if (seen.containsKey(other)) {
          continue;
        }
        GraphNodeOutput node = ring.nodes.get(other);
        if (node == null || !visible.test(node)) {
          continue;
        }
        seen.put(other, node);
        parent.put(other, current.nodeId());
        parentEdge.put(other, e);
        depth.put(other, depth.get(current.nodeId()) + 1);
        if (other.equals(goal)) {
          found = true;
          break;
        }
        queue.add(node);
      }
    }
    Slice slice = new Slice();
    List<String> path = new ArrayList<>();
    if (found) {
      for (String at = goal; at != null; at = parent.get(at)) {
        path.add(0, at);
      }
      for (String id : path) {
        GraphNodeOutput n = seen.get(id);
        slice.node(n.nodeType(), n.entityId(), n.label(), n.subtype());
        GraphEdgeOutput e = parentEdge.get(id);
        if (e != null) {
          slice.edge(e.edgeType(), e.source(), e.target(), e.result());
        }
      }
      // The start node was seeded with its id as label: read it back from its own ring.
      if (path.size() > 1) {
        Slice startRing = new Slice();
        expand(startRing, tenantIds, fromType, fromId, Set.of(), 1);
        GraphNodeOutput real = startRing.nodes.get(start);
        if (real != null) {
          slice.nodes.put(start, real);
        }
      }
    }
    return slice.build(start, visible, path);
  }

  /**
   * The node gate of one request, memoised per node: simulations and scenarios by their own grant,
   * an inject through its simulation or scenario (one read of its parents), else as an atomic
   * testing.
   */
  Predicate<GraphNodeOutput> gate(Collection<String> tenantIds, GraphVisibility visibility) {
    Map<String, Boolean> decided = new HashMap<>();
    return node -> {
      if (node == null) {
        return false;
      }
      return decided.computeIfAbsent(
          node.nodeId(),
          k ->
              switch (node.nodeType()) {
                case SIMULATION -> visibility.canReadSimulation(node.entityId());
                case SCENARIO -> visibility.canReadScenario(node.entityId());
                case INJECT -> {
                  List<Object[]> parents =
                      entityManager
                          .createQuery(
                              "SELECT x.id, s.id FROM Inject i LEFT JOIN i.exercise x"
                                  + " LEFT JOIN i.scenario s"
                                  + " WHERE i.id = :id AND i.tenant.id IN :tenantIds",
                              Object[].class)
                          .setParameter("id", node.entityId())
                          .setParameter("tenantIds", tenantIds)
                          .setMaxResults(1)
                          .getResultList();
                  if (parents.isEmpty()) {
                    yield false;
                  }
                  Object[] p = parents.get(0);
                  if (p[0] != null) {
                    yield visibility.canReadSimulation((String) p[0]);
                  }
                  if (p[1] != null) {
                    yield visibility.canReadScenario((String) p[1]);
                  }
                  yield visibility.canReadAtomicTesting(node.entityId());
                }
                default -> true;
              });
    };
  }

  // -- Expansion, one relation at a time --

  /** Adds the focus node and its ring to {@code slice}; returns the focus node id. */
  String expand(
      Slice slice,
      Collection<String> tenantIds,
      GraphNodeType type,
      String id,
      Set<GraphEdgeType> edgeTypes,
      int cap) {
    Predicate<GraphEdgeType> want = t -> edgeTypes == null || edgeTypes.isEmpty() || edgeTypes.contains(t);
    String focus;
    switch (type) {
      case ASSET -> {
        focus = assetNode(slice, tenantIds, id);
        if (focus == null) {
          return null;
        }
        if (want.test(GraphEdgeType.MEMBER_OF)) {
          rows(
              slice,
              "SELECT g.id, g.name FROM AssetGroup g JOIN g.assets a"
                  + " WHERE a.id = :id AND g.tenant.id IN :tenantIds ORDER BY g.name",
              tenantIds,
              id,
              cap,
              r -> {
                String g = slice.node(GraphNodeType.ASSET_GROUP, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.MEMBER_OF, focus, g, null);
              });
        }
        if (want.test(GraphEdgeType.FOUND_ON)) {
          rows(
              slice,
              "SELECT f.id, f.valueDisplay, f.type FROM Finding f JOIN f.assets a"
                  + " WHERE a.id = :id AND f.tenant.id IN :tenantIds ORDER BY f.updateDate DESC",
              tenantIds,
              id,
              cap,
              r -> {
                String f = slice.node(GraphNodeType.FINDING, (String) r[0], (String) r[1], str(r[2]));
                slice.edge(GraphEdgeType.FOUND_ON, f, focus, null);
              });
        }
        if (want.test(GraphEdgeType.TARGETS)) {
          targets(slice, tenantIds, "e.asset.id = :id", id, cap);
        }
      }
      case ASSET_GROUP -> {
        focus =
            single(
                slice,
                "SELECT g.id, g.name FROM AssetGroup g WHERE g.id = :id AND g.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> slice.node(GraphNodeType.ASSET_GROUP, (String) r[0], (String) r[1], null));
        if (focus == null) {
          return null;
        }
        if (want.test(GraphEdgeType.MEMBER_OF)) {
          rows(
              slice,
              "SELECT a.id, a.name, a.type FROM AssetGroup g JOIN g.assets a"
                  + " WHERE g.id = :id AND a.tenant.id IN :tenantIds ORDER BY a.name",
              tenantIds,
              id,
              cap,
              r -> {
                String a = slice.node(GraphNodeType.ASSET, (String) r[0], (String) r[1], (String) r[2]);
                slice.edge(GraphEdgeType.MEMBER_OF, focus, a, null);
              });
        }
        if (want.test(GraphEdgeType.TARGETS)) {
          targets(slice, tenantIds, "e.assetGroup.id = :id", id, cap);
        }
      }
      case FINDING -> {
        focus =
            single(
                slice,
                "SELECT f.id, f.valueDisplay, f.type FROM Finding f"
                    + " WHERE f.id = :id AND f.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> slice.node(GraphNodeType.FINDING, (String) r[0], (String) r[1], str(r[2])));
        if (focus == null) {
          return null;
        }
        if (want.test(GraphEdgeType.FOUND_ON)) {
          rows(
              slice,
              "SELECT a.id, a.name, a.type FROM Finding f JOIN f.assets a"
                  + " WHERE f.id = :id AND a.tenant.id IN :tenantIds ORDER BY a.name",
              tenantIds,
              id,
              cap,
              r -> {
                String a = slice.node(GraphNodeType.ASSET, (String) r[0], (String) r[1], (String) r[2]);
                slice.edge(GraphEdgeType.FOUND_ON, focus, a, null);
              });
        }
        if (want.test(GraphEdgeType.PRODUCED)) {
          rows(
              slice,
              "SELECT DISTINCT i.id, i.title FROM FindingOccurrence o JOIN o.inject i"
                  + " WHERE o.finding.id = :id AND i.tenant.id IN :tenantIds",
              tenantIds,
              id,
              cap,
              r -> {
                String i = slice.node(GraphNodeType.INJECT, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.PRODUCED, i, focus, null);
              });
        }
      }
      case INJECT -> {
        focus =
            single(
                slice,
                "SELECT i.id, i.title FROM Inject i WHERE i.id = :id AND i.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> slice.node(GraphNodeType.INJECT, (String) r[0], (String) r[1], null));
        if (focus == null) {
          return null;
        }
        if (want.test(GraphEdgeType.TARGETS)) {
          targets(slice, tenantIds, "i.id = :id", id, cap);
        }
        if (want.test(GraphEdgeType.PRODUCED)) {
          rows(
              slice,
              "SELECT DISTINCT f.id, f.valueDisplay, f.type FROM FindingOccurrence o JOIN o.finding f"
                  + " WHERE o.inject.id = :id AND f.tenant.id IN :tenantIds",
              tenantIds,
              id,
              cap,
              r -> {
                String f = slice.node(GraphNodeType.FINDING, (String) r[0], (String) r[1], str(r[2]));
                slice.edge(GraphEdgeType.PRODUCED, focus, f, null);
              });
        }
        if (want.test(GraphEdgeType.PART_OF)) {
          rows(
              slice,
              "SELECT x.id, x.name FROM Inject i JOIN i.exercise x"
                  + " WHERE i.id = :id AND x.tenant.id IN :tenantIds",
              tenantIds,
              id,
              1,
              r -> {
                String s = slice.node(GraphNodeType.SIMULATION, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.PART_OF, focus, s, null);
              });
        }
      }
      case SIMULATION -> {
        focus =
            single(
                slice,
                "SELECT x.id, x.name FROM Exercise x WHERE x.id = :id AND x.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> slice.node(GraphNodeType.SIMULATION, (String) r[0], (String) r[1], null));
        if (focus == null) {
          return null;
        }
        if (want.test(GraphEdgeType.PART_OF)) {
          rows(
              slice,
              "SELECT i.id, i.title FROM Inject i WHERE i.exercise.id = :id"
                  + " AND i.tenant.id IN :tenantIds ORDER BY i.title",
              tenantIds,
              id,
              cap,
              r -> {
                String i = slice.node(GraphNodeType.INJECT, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.PART_OF, i, focus, null);
              });
        }
        if (want.test(GraphEdgeType.RUN_OF)) {
          rows(
              slice,
              "SELECT s.id, s.name FROM Exercise x JOIN x.scenario s"
                  + " WHERE x.id = :id AND x.tenant.id IN :tenantIds",
              tenantIds,
              id,
              1,
              r -> {
                String s = slice.node(GraphNodeType.SCENARIO, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.RUN_OF, focus, s, null);
              });
        }
      }
      case SCENARIO -> {
        // A scenario is reached through its simulations, so its scope is theirs: a scenario
        // without a simulation in the caller's tenants is not part of the graph.
        focus = null;
        List<Object[]> runs =
            entityManager
                .createQuery(
                    "SELECT x.id, x.name, s.name FROM Exercise x JOIN x.scenario s"
                        + " WHERE s.id = :id AND x.tenant.id IN :tenantIds"
                        + " ORDER BY x.start DESC NULLS LAST",
                    Object[].class)
                .setParameter("id", id)
                .setParameter("tenantIds", tenantIds)
                .setMaxResults(cap + 1)
                .getResultList();
        if (runs.isEmpty()) {
          return null;
        }
        focus = slice.node(GraphNodeType.SCENARIO, id, (String) runs.get(0)[2], null);
        if (runs.size() > cap) {
          slice.truncated = true;
          runs = runs.subList(0, cap);
        }
        if (want.test(GraphEdgeType.RUN_OF)) {
          for (Object[] r : runs) {
            String s = slice.node(GraphNodeType.SIMULATION, (String) r[0], (String) r[1], null);
            slice.edge(GraphEdgeType.RUN_OF, s, focus, null);
          }
        }
      }
      default -> throw new IllegalArgumentException("Unsupported graph entity type " + type);
    }
    return focus;
  }

  /** Inject -> asset / asset group edges with the worst expectation result per pair. */
  private void targets(
      Slice slice, Collection<String> tenantIds, String predicate, String id, int cap) {
    List<Object[]> rows =
        entityManager
            .createQuery(
                "SELECT i.id, i.title, a.id, a.name, a.type, g.id, g.name, e.score, e.expectedScore"
                    + " FROM TechnicalInjectExpectation e JOIN e.inject i"
                    + " LEFT JOIN e.asset a LEFT JOIN e.assetGroup g"
                    + " WHERE "
                    + predicate
                    + " AND i.tenant.id IN :tenantIds"
                    + " AND (a IS NULL OR a.tenant.id IN :tenantIds)"
                    + " AND (g IS NULL OR g.tenant.id IN :tenantIds)"
                    + " ORDER BY e.createdAt DESC",
                Object[].class)
            .setParameter("id", id)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(cap * 4 + 1)
            .getResultList();
    if (rows.size() > cap * 4) {
      slice.truncated = true;
    }
    int pairs = 0;
    for (Object[] r : rows) {
      GraphResult result = GraphResult.ofScore((Double) r[7], (Double) r[8]);
      String inject = slice.node(GraphNodeType.INJECT, (String) r[0], (String) r[1], null);
      String target;
      if (r[2] != null) {
        target = slice.node(GraphNodeType.ASSET, (String) r[2], (String) r[3], (String) r[4]);
      } else if (r[5] != null) {
        target = slice.node(GraphNodeType.ASSET_GROUP, (String) r[5], (String) r[6], null);
      } else {
        continue;
      }
      boolean known =
          slice.edges.containsKey(GraphEdgeOutput.idOf(GraphEdgeType.TARGETS, inject, target));
      if (!known && ++pairs > cap) {
        slice.truncated = true;
        break;
      }
      slice.edge(GraphEdgeType.TARGETS, inject, target, result);
    }
  }

  private String assetNode(Slice slice, Collection<String> tenantIds, String id) {
    return single(
        slice,
        "SELECT a.id, a.name, a.type FROM Asset a WHERE a.id = :id AND a.tenant.id IN :tenantIds",
        tenantIds,
        id,
        r -> slice.node(GraphNodeType.ASSET, (String) r[0], (String) r[1], (String) r[2]));
  }

  private String single(
      Slice slice,
      String jpql,
      Collection<String> tenantIds,
      String id,
      java.util.function.Function<Object[], String> map) {
    List<Object[]> rows =
        entityManager
            .createQuery(jpql, Object[].class)
            .setParameter("id", id)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(1)
            .getResultList();
    return rows.isEmpty() ? null : map.apply(rows.get(0));
  }

  private void rows(
      Slice slice,
      String jpql,
      Collection<String> tenantIds,
      String id,
      int cap,
      java.util.function.Consumer<Object[]> each) {
    List<Object[]> rows =
        entityManager
            .createQuery(jpql, Object[].class)
            .setParameter("id", id)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(cap + 1)
            .getResultList();
    if (rows.size() > cap) {
      slice.truncated = true;
      rows = rows.subList(0, cap);
    }
    rows.forEach(each);
  }

  private static String str(Object value) {
    return value == null ? null : value.toString();
  }

  static int clamp(int limit) {
    return Math.min(Math.max(limit, 1), MAX_LIMIT);
  }
}
