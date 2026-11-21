package io.openaev.api.graph;

import io.openaev.api.graph.dto.GraphEdgeOutput;
import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeOutput;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphResult;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.expectation.ExpectationType;
import io.openaev.service.AssetGroupService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * The exploration graph: lazy slices of the asset / asset group / finding / inject / simulation /
 * scenario graph, read on demand, never the whole tenant.
 *
 * <p>Every read carries an explicit {@code tenant.id IN :tenantIds} predicate on each entity it
 * returns, so isolation holds whether the table is v1 ({@code @Filter}) or v2-active (statement
 * inspector), and the caller's {@code TxCtx} scope bounds the tenant set. Every read is capped at
 * {@code limit + 1} rows: the extra row only tells the UI there is more ("load more").
 *
 * <p>Resource RBAC on simulations and scenarios is applied by the caller through {@code visible}: a
 * node the caller may not read is dropped with its edges.
 */
@Service
public class GraphExplorationService {

  /** Hard ceiling of nodes one request can return, whatever the client asks. */
  public static final int MAX_LIMIT = 200;

  /** Hard ceiling of a path search: hops and visited nodes. */
  static final int MAX_PATH_DEPTH = 6;

  static final int MAX_PATH_VISITED = 2_000;

  /** Dynamic asset groups whose filter is tested against one asset, at most, per expansion. */
  static final int MAX_DYNAMIC_GROUPS = 50;

  private static final String PREVENTED = ExpectationType.PREVENTION.successLabel;

  @PersistenceContext private EntityManager entityManager;

  @Autowired @Lazy private AssetGroupService assetGroupService;

  /** Builder of one slice, deduplicating nodes and edges as rows come in. */
  static final class Slice {
    final Map<String, GraphNodeOutput> nodes = new LinkedHashMap<>();
    final Map<String, GraphEdgeOutput> edges = new LinkedHashMap<>();
    final Map<String, GraphResult> nodeResults = new HashMap<>();
    final Map<String, Long> failed = new HashMap<>();

    /** The date of a node where its entity has one (see {@link GraphNodeOutput#date()}). */
    final Map<String, Instant> dates = new HashMap<>();

    /**
     * Attack-path steps: per edge, the worst verdict per simulation that drew it. A step is only as
     * visible as one of its simulations, and its verdict is the worst over the visible ones, so a
     * simulation the caller may not read never leaks through a shared edge.
     */
    final Map<String, Map<String, GraphResult>> stepSimulations = new HashMap<>();

    boolean truncated;

    String node(GraphNodeType type, String id, String label, String subtype) {
      String nodeId = GraphNodeOutput.idOf(type, id);
      nodes.putIfAbsent(
          nodeId,
          new GraphNodeOutput(
              nodeId, type, id, label == null ? id : label, subtype, null, 0, 0, null));
      return nodeId;
    }

    /** Records a node's date; the latest wins when several rows carry one. */
    void date(String nodeId, Object value) {
      if (value instanceof Instant instant) {
        dates.merge(nodeId, instant, (a, b) -> a.isAfter(b) ? a : b);
      }
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
    }

    /** Records an attack-path step of one simulation between two asset nodes. */
    void step(String source, String target, String simulationId, GraphResult result) {
      String id = GraphEdgeOutput.idOf(GraphEdgeType.ATTACK_STEP, source, target);
      edges.putIfAbsent(
          id, new GraphEdgeOutput(id, GraphEdgeType.ATTACK_STEP, source, target, null));
      stepSimulations
          .computeIfAbsent(id, k -> new HashMap<>())
          .merge(simulationId, result, GraphResult::worst);
    }

    GraphOutput build(String focus, Predicate<GraphNodeOutput> visible, List<String> path) {
      Set<String> visibleIds = new java.util.HashSet<>();
      for (GraphNodeOutput n : nodes.values()) {
        if (visible.test(n)) {
          visibleIds.add(n.nodeId());
        }
      }
      // A finding count counts the visible findings only: a hidden finding never shows as a number.
      Map<String, Long> findingCounts = new HashMap<>();
      for (GraphEdgeOutput e : edges.values()) {
        if (e.edgeType() == GraphEdgeType.FOUND_ON && visibleIds.contains(e.source())) {
          findingCounts.merge(e.target(), 1L, Long::sum);
        }
      }
      List<GraphNodeOutput> kept = new ArrayList<>();
      for (GraphNodeOutput n : nodes.values()) {
        if (!visibleIds.contains(n.nodeId())) {
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
                findingCounts.getOrDefault(n.nodeId(), 0L),
                dates.get(n.nodeId())));
      }
      List<GraphEdgeOutput> keptEdges = new ArrayList<>();
      for (GraphEdgeOutput e : edges.values()) {
        if (!visibleIds.contains(e.source()) || !visibleIds.contains(e.target())) {
          continue;
        }
        Map<String, GraphResult> sims = stepSimulations.get(e.edgeId());
        if (sims == null) {
          keptEdges.add(e);
          continue;
        }
        GraphResult worst = null;
        boolean any = false;
        for (Map.Entry<String, GraphResult> s : sims.entrySet()) {
          if (visible.test(simulationProbe(s.getKey()))) {
            any = true;
            worst = GraphResult.worst(worst, s.getValue());
          }
        }
        if (any) {
          keptEdges.add(
              new GraphEdgeOutput(e.edgeId(), e.edgeType(), e.source(), e.target(), worst));
        }
      }
      return new GraphOutput(focus, kept, keptEdges, truncated, path);
    }
  }

  /** A simulation node used only to ask the gate whether the caller reads that simulation. */
  static GraphNodeOutput simulationProbe(String simulationId) {
    return new GraphNodeOutput(
        GraphNodeOutput.idOf(GraphNodeType.SIMULATION, simulationId),
        GraphNodeType.SIMULATION,
        simulationId,
        simulationId,
        null,
        null,
        0,
        0,
        null);
  }

  /**
   * The verdict of one attack-path execution, read the way the attack path view colours it:
   * prevented is a success, anything else that was evaluated is a failure (detected but not
   * prevented included), and no verdict at all is pending.
   */
  static GraphResult stepResult(String preventionStatus, String detectionStatus) {
    if (PREVENTED.equals(preventionStatus)) {
      return GraphResult.SUCCESS;
    }
    if (preventionStatus == null && detectionStatus == null) {
      return GraphResult.PENDING;
    }
    return GraphResult.FAILED;
  }

  /** A person's label: full name, else email, else id. */
  static String personLabel(Object id, Object firstname, Object lastname, Object email) {
    String name =
        ((firstname == null ? "" : firstname.toString())
                + " "
                + (lastname == null ? "" : lastname.toString()))
            .trim();
    if (!name.isEmpty()) {
      return name;
    }
    return email != null ? email.toString() : str(id);
  }

  // -- Public reads --

  /**
   * The first ring of an entity: every relation of the focus node, each capped at {@code limit}. An
   * empty slice (not an error) when the entity is outside the scope.
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
   *
   * <p>The ring also carries the attack-path steps that leave or reach each hot asset and the
   * person the asset is the identity of, so the landing shows how an exposed asset chains to the
   * next one and whose account it is, not only what failed on it. Both stay capped per asset.
   */
  public GraphOutput hotPaths(Collection<String> tenantIds, int limit, GraphVisibility visibility) {
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
          Set.of(
              GraphEdgeType.MEMBER_OF,
              GraphEdgeType.FOUND_ON,
              GraphEdgeType.TARGETS,
              GraphEdgeType.ATTACK_STEP,
              GraphEdgeType.IDENTITY_OF);
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
        new GraphNodeOutput(start, fromType, fromId, fromId, null, null, 0, 0, null);
    Map<String, Instant> seenDates = new HashMap<>();
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
      ring.dates.forEach(seenDates::putIfAbsent);
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
        slice.date(id, seenDates.get(id));
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
          slice.date(start, startRing.dates.get(start));
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
                case PERSON, ORGANIZATION, TEAM -> visibility.canReadPeople(node.nodeType());
                case FINDING -> visibility.canReadFindings();
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
    Predicate<GraphEdgeType> want =
        t -> edgeTypes == null || edgeTypes.isEmpty() || edgeTypes.contains(t);
    String focus;
    switch (type) {
      case ASSET -> {
        focus = assetNode(slice, tenantIds, id);
        if (focus == null) {
          return null;
        }
        final String here = focus;
        if (want.test(GraphEdgeType.MEMBER_OF)) {
          rows(
              slice,
              "SELECT g.id, g.name FROM AssetGroup g JOIN g.assets a"
                  + " WHERE a.id = :id AND g.tenant.id IN :tenantIds ORDER BY g.name",
              tenantIds,
              id,
              cap,
              r -> {
                String g =
                    slice.node(GraphNodeType.ASSET_GROUP, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.MEMBER_OF, here, g, null);
              });
          dynamicGroupsOf(slice, tenantIds, id, here, cap);
        }
        if (want.test(GraphEdgeType.IDENTITY_OF)) {
          rows(
              slice,
              "SELECT u.id, u.firstname, u.lastname, u.email FROM Asset a, User u"
                  + " WHERE a.id = :id AND u.id = a.linkedPerson AND a.tenant.id IN :tenantIds",
              tenantIds,
              id,
              1,
              r -> {
                String p =
                    slice.node(
                        GraphNodeType.PERSON,
                        (String) r[0],
                        personLabel(r[0], r[1], r[2], r[3]),
                        null);
                slice.edge(GraphEdgeType.IDENTITY_OF, here, p, null);
              });
        }
        if (want.test(GraphEdgeType.LINKED_TO)) {
          linkedAssets(slice, tenantIds, id, here, cap);
        }
        if (want.test(GraphEdgeType.ATTACK_STEP)) {
          steps(slice, tenantIds, STEPS_OF_ASSET, id, cap);
        }
        if (want.test(GraphEdgeType.FOUND_ON)) {
          rows(
              slice,
              "SELECT f.id, f.valueDisplay, f.type, f.updateDate FROM Finding f JOIN f.assets a"
                  + " WHERE a.id = :id AND f.tenant.id IN :tenantIds ORDER BY f.updateDate DESC",
              tenantIds,
              id,
              cap,
              r -> {
                String f =
                    slice.node(GraphNodeType.FINDING, (String) r[0], (String) r[1], str(r[2]));
                slice.date(f, r[3]);
                slice.edge(GraphEdgeType.FOUND_ON, f, here, null);
              });
        }
        if (want.test(GraphEdgeType.TARGETS)) {
          targets(slice, tenantIds, TARGETS_OF_ASSET, id, cap);
        }
      }
      case ASSET_GROUP -> {
        focus =
            single(
                slice,
                "SELECT g.id, g.name FROM AssetGroup g"
                    + " WHERE g.id = :id AND g.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> slice.node(GraphNodeType.ASSET_GROUP, (String) r[0], (String) r[1], null));
        if (focus == null) {
          return null;
        }
        final String here = focus;
        if (want.test(GraphEdgeType.MEMBER_OF)) {
          rows(
              slice,
              "SELECT a.id, a.name, a.type FROM AssetGroup g JOIN g.assets a"
                  + " WHERE g.id = :id AND a.tenant.id IN :tenantIds ORDER BY a.name",
              tenantIds,
              id,
              cap,
              r -> {
                String a =
                    slice.node(GraphNodeType.ASSET, (String) r[0], (String) r[1], (String) r[2]);
                slice.edge(GraphEdgeType.MEMBER_OF, here, a, null);
              });
          dynamicMembersOf(slice, tenantIds, id, here, cap);
        }
        if (want.test(GraphEdgeType.TARGETS)) {
          targets(slice, tenantIds, TARGETS_OF_ASSET_GROUP, id, cap);
        }
      }
      case FINDING -> {
        focus =
            single(
                slice,
                "SELECT f.id, f.valueDisplay, f.type, f.updateDate FROM Finding f"
                    + " WHERE f.id = :id AND f.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> {
                  String f =
                      slice.node(GraphNodeType.FINDING, (String) r[0], (String) r[1], str(r[2]));
                  slice.date(f, r[3]);
                  return f;
                });
        if (focus == null) {
          return null;
        }
        final String here = focus;
        if (want.test(GraphEdgeType.FOUND_ON)) {
          rows(
              slice,
              "SELECT a.id, a.name, a.type FROM Finding f JOIN f.assets a"
                  + " WHERE f.id = :id AND a.tenant.id IN :tenantIds ORDER BY a.name",
              tenantIds,
              id,
              cap,
              r -> {
                String a =
                    slice.node(GraphNodeType.ASSET, (String) r[0], (String) r[1], (String) r[2]);
                slice.edge(GraphEdgeType.FOUND_ON, here, a, null);
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
                slice.edge(GraphEdgeType.PRODUCED, i, here, null);
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
        final String here = focus;
        if (want.test(GraphEdgeType.TARGETS)) {
          targets(slice, tenantIds, TARGETS_OF_INJECT, id, cap);
        }
        if (want.test(GraphEdgeType.PRODUCED)) {
          rows(
              slice,
              "SELECT DISTINCT f.id, f.valueDisplay, f.type, f.updateDate"
                  + " FROM FindingOccurrence o JOIN o.finding f"
                  + " WHERE o.inject.id = :id AND f.tenant.id IN :tenantIds",
              tenantIds,
              id,
              cap,
              r -> {
                String f =
                    slice.node(GraphNodeType.FINDING, (String) r[0], (String) r[1], str(r[2]));
                slice.date(f, r[3]);
                slice.edge(GraphEdgeType.PRODUCED, here, f, null);
              });
        }
        if (want.test(GraphEdgeType.PART_OF)) {
          rows(
              slice,
              "SELECT x.id, x.name, x.start FROM Inject i JOIN i.exercise x"
                  + " WHERE i.id = :id AND x.tenant.id IN :tenantIds",
              tenantIds,
              id,
              1,
              r -> {
                String s = slice.node(GraphNodeType.SIMULATION, (String) r[0], (String) r[1], null);
                slice.date(s, r[2]);
                slice.edge(GraphEdgeType.PART_OF, here, s, null);
              });
        }
      }
      case SIMULATION -> {
        focus =
            single(
                slice,
                "SELECT x.id, x.name, x.start FROM Exercise x"
                    + " WHERE x.id = :id AND x.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> {
                  String s =
                      slice.node(GraphNodeType.SIMULATION, (String) r[0], (String) r[1], null);
                  slice.date(s, r[2]);
                  return s;
                });
        if (focus == null) {
          return null;
        }
        final String here = focus;
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
                slice.edge(GraphEdgeType.PART_OF, i, here, null);
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
                slice.edge(GraphEdgeType.RUN_OF, here, s, null);
              });
        }
        if (want.test(GraphEdgeType.ATTACK_STEP)) {
          steps(slice, tenantIds, STEPS_OF_SIMULATION, id, cap);
        }
      }
      case PERSON -> {
        focus = personNode(slice, tenantIds, id);
        if (focus == null) {
          return null;
        }
        final String here = focus;
        if (want.test(GraphEdgeType.IDENTITY_OF)) {
          rows(
              slice,
              "SELECT a.id, a.name, a.type FROM Asset a"
                  + " WHERE a.linkedPerson = :id AND a.tenant.id IN :tenantIds ORDER BY a.name",
              tenantIds,
              id,
              cap,
              r -> {
                String a =
                    slice.node(GraphNodeType.ASSET, (String) r[0], (String) r[1], (String) r[2]);
                slice.edge(GraphEdgeType.IDENTITY_OF, a, here, null);
              });
        }
        if (want.test(GraphEdgeType.WORKS_FOR)) {
          rows(
              slice,
              "SELECT o.id, o.name FROM Organization o JOIN o.users u"
                  + " WHERE u.id = :id AND o.tenant.id IN :tenantIds ORDER BY o.name",
              tenantIds,
              id,
              cap,
              r -> {
                String o =
                    slice.node(GraphNodeType.ORGANIZATION, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.WORKS_FOR, here, o, null);
              });
        }
        if (want.test(GraphEdgeType.IN_TEAM)) {
          rows(
              slice,
              "SELECT t.id, t.name FROM Team t JOIN t.users u"
                  + " WHERE u.id = :id AND t.tenant.id IN :tenantIds ORDER BY t.name",
              tenantIds,
              id,
              cap,
              r -> {
                String t = slice.node(GraphNodeType.TEAM, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.IN_TEAM, here, t, null);
              });
        }
      }
      case ORGANIZATION -> {
        focus =
            single(
                slice,
                "SELECT o.id, o.name FROM Organization o"
                    + " WHERE o.id = :id AND o.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> slice.node(GraphNodeType.ORGANIZATION, (String) r[0], (String) r[1], null));
        if (focus == null) {
          return null;
        }
        final String here = focus;
        if (want.test(GraphEdgeType.WORKS_FOR)) {
          rows(
              slice,
              "SELECT u.id, u.firstname, u.lastname, u.email FROM Organization o JOIN o.users u"
                  + " WHERE o.id = :id AND o.tenant.id IN :tenantIds"
                  + " ORDER BY u.lastname, u.firstname, u.email",
              tenantIds,
              id,
              cap,
              r -> {
                String p =
                    slice.node(
                        GraphNodeType.PERSON,
                        (String) r[0],
                        personLabel(r[0], r[1], r[2], r[3]),
                        null);
                slice.edge(GraphEdgeType.WORKS_FOR, p, here, null);
              });
        }
        if (want.test(GraphEdgeType.TEAM_OF)) {
          rows(
              slice,
              "SELECT t.id, t.name FROM Team t"
                  + " WHERE t.organization.id = :id AND t.tenant.id IN :tenantIds ORDER BY t.name",
              tenantIds,
              id,
              cap,
              r -> {
                String t = slice.node(GraphNodeType.TEAM, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.TEAM_OF, t, here, null);
              });
        }
      }
      case TEAM -> {
        focus =
            single(
                slice,
                "SELECT t.id, t.name FROM Team t WHERE t.id = :id AND t.tenant.id IN :tenantIds",
                tenantIds,
                id,
                r -> slice.node(GraphNodeType.TEAM, (String) r[0], (String) r[1], null));
        if (focus == null) {
          return null;
        }
        final String here = focus;
        if (want.test(GraphEdgeType.IN_TEAM)) {
          rows(
              slice,
              "SELECT u.id, u.firstname, u.lastname, u.email FROM Team t JOIN t.users u"
                  + " WHERE t.id = :id AND t.tenant.id IN :tenantIds"
                  + " ORDER BY u.lastname, u.firstname, u.email",
              tenantIds,
              id,
              cap,
              r -> {
                String p =
                    slice.node(
                        GraphNodeType.PERSON,
                        (String) r[0],
                        personLabel(r[0], r[1], r[2], r[3]),
                        null);
                slice.edge(GraphEdgeType.IN_TEAM, p, here, null);
              });
        }
        if (want.test(GraphEdgeType.TEAM_OF)) {
          rows(
              slice,
              "SELECT o.id, o.name FROM Team t JOIN t.organization o"
                  + " WHERE t.id = :id AND t.tenant.id IN :tenantIds AND o.tenant.id IN :tenantIds",
              tenantIds,
              id,
              1,
              r -> {
                String o =
                    slice.node(GraphNodeType.ORGANIZATION, (String) r[0], (String) r[1], null);
                slice.edge(GraphEdgeType.TEAM_OF, here, o, null);
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
                    "SELECT x.id, x.name, s.name, x.start FROM Exercise x JOIN x.scenario s"
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
            slice.date(s, r[3]);
            slice.edge(GraphEdgeType.RUN_OF, s, focus, null);
          }
        }
      }
      default -> throw new IllegalArgumentException("Unsupported graph entity type " + type);
    }
    return focus;
  }

  private static final String TARGETS_SELECT =
      "SELECT i.id, i.title, a.id, a.name, a.type, g.id, g.name, e.score, e.expectedScore"
          + " FROM TechnicalInjectExpectation e JOIN e.inject i"
          + " LEFT JOIN e.asset a LEFT JOIN e.assetGroup g";

  private static final String TARGETS_SCOPE =
      " AND i.tenant.id IN :tenantIds"
          + " AND (a IS NULL OR a.tenant.id IN :tenantIds)"
          + " AND (g IS NULL OR g.tenant.id IN :tenantIds)"
          + " ORDER BY e.createdAt DESC";

  /** The expectations on one asset. */
  static final String TARGETS_OF_ASSET = TARGETS_SELECT + " WHERE e.asset.id = :id" + TARGETS_SCOPE;

  /** The expectations on one asset group. */
  static final String TARGETS_OF_ASSET_GROUP =
      TARGETS_SELECT + " WHERE e.assetGroup.id = :id" + TARGETS_SCOPE;

  /** The expectations of one inject. */
  static final String TARGETS_OF_INJECT = TARGETS_SELECT + " WHERE i.id = :id" + TARGETS_SCOPE;

  /**
   * Inject -> asset / asset group edges with the worst expectation result per pair, read by {@code
   * query} (one of the three constant queries above, never a string built from input).
   */
  void targets(Slice slice, Collection<String> tenantIds, String query, String id, int cap) {
    if (!TARGETS_OF_ASSET.equals(query)
        && !TARGETS_OF_ASSET_GROUP.equals(query)
        && !TARGETS_OF_INJECT.equals(query)) {
      throw new IllegalArgumentException("Unknown target query");
    }
    List<Object[]> rows =
        entityManager
            .createQuery(query, Object[].class)
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

  /** The attack-path steps an asset takes part in, as source or target. */
  static final String STEPS_OF_ASSET =
      "SELECT x.simulationId, sa.id, sa.name, sa.type, ta.id, ta.name, ta.type,"
          + " x.preventionStatus, x.detectionStatus"
          + " FROM AttackPathExecution x, Asset sa, Asset ta"
          + " WHERE (x.sourceAssetId = :id OR x.targetAssetId = :id)"
          + " AND sa.id = x.sourceAssetId AND ta.id = x.targetAssetId"
          + " AND x.tenant.id IN :tenantIds"
          + " AND sa.tenant.id IN :tenantIds AND ta.tenant.id IN :tenantIds"
          + " ORDER BY x.executedAt DESC";

  /** The attack-path steps of one simulation. */
  static final String STEPS_OF_SIMULATION =
      "SELECT x.simulationId, sa.id, sa.name, sa.type, ta.id, ta.name, ta.type,"
          + " x.preventionStatus, x.detectionStatus"
          + " FROM AttackPathExecution x, Asset sa, Asset ta"
          + " WHERE x.simulationId = :id"
          + " AND sa.id = x.sourceAssetId AND ta.id = x.targetAssetId"
          + " AND x.tenant.id IN :tenantIds"
          + " AND sa.tenant.id IN :tenantIds AND ta.tenant.id IN :tenantIds"
          + " ORDER BY x.executedAt DESC";

  /**
   * Attack-path steps: source asset to target asset of the attack-path executions read by {@code
   * query} (one of the two constant queries above, never a string built from input), each pair
   * carrying the worst verdict per simulation. Discovered targets (no asset) and injector sources
   * (no asset) are not part of the estate graph and are skipped.
   */
  void steps(Slice slice, Collection<String> tenantIds, String query, String id, int cap) {
    if (!STEPS_OF_ASSET.equals(query) && !STEPS_OF_SIMULATION.equals(query)) {
      throw new IllegalArgumentException("Unknown attack-path step query");
    }
    List<Object[]> rows =
        entityManager
            .createQuery(query, Object[].class)
            .setParameter("id", id)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(cap * 4 + 1)
            .getResultList();
    if (rows.size() > cap * 4) {
      slice.truncated = true;
    }
    int pairs = 0;
    for (Object[] r : rows) {
      if (r[1].equals(r[4])) {
        continue;
      }
      String source = GraphNodeOutput.idOf(GraphNodeType.ASSET, (String) r[1]);
      String target = GraphNodeOutput.idOf(GraphNodeType.ASSET, (String) r[4]);
      boolean known =
          slice.edges.containsKey(GraphEdgeOutput.idOf(GraphEdgeType.ATTACK_STEP, source, target));
      if (!known && ++pairs > cap) {
        slice.truncated = true;
        break;
      }
      slice.node(GraphNodeType.ASSET, (String) r[1], (String) r[2], (String) r[3]);
      slice.node(GraphNodeType.ASSET, (String) r[4], (String) r[5], (String) r[6]);
      slice.step(source, target, (String) r[0], stepResult(str(r[7]), str(r[8])));
    }
  }

  /**
   * The assets linked to an asset in either direction ({@code assets_links}): an identity logged on
   * to an endpoint, a service running on a host. The edge keeps the stored direction (source = the
   * asset the link was added from); several typed links between the same two assets draw one edge,
   * the types being listed on the asset page.
   */
  private void linkedAssets(
      Slice slice, Collection<String> tenantIds, String assetId, String here, int cap) {
    rows(
        slice,
        "SELECT DISTINCT b.id, b.name, b.type, CASE WHEN l.assetId = :id THEN 1 ELSE 0 END"
            + " FROM AssetLink l, Asset b"
            + " WHERE ((l.assetId = :id AND b.id = l.linkedAssetId)"
            + " OR (l.linkedAssetId = :id AND b.id = l.assetId))"
            + " AND b.tenant.id IN :tenantIds ORDER BY b.name",
        tenantIds,
        assetId,
        cap,
        r -> {
          String other = slice.node(GraphNodeType.ASSET, (String) r[0], (String) r[1], str(r[2]));
          boolean outgoing = ((Number) r[3]).intValue() == 1;
          slice.edge(
              GraphEdgeType.LINKED_TO, outgoing ? here : other, outgoing ? other : here, null);
        });
  }

  /**
   * The asset groups an asset belongs to through their dynamic filter: each dynamic group of the
   * scope is tested with one id-constrained query, at most {@value #MAX_DYNAMIC_GROUPS} of them, so
   * a member list is never materialised.
   */
  private void dynamicGroupsOf(
      Slice slice, Collection<String> tenantIds, String assetId, String here, int cap) {
    if (assetGroupService == null) {
      return;
    }
    Asset asset = entityManager.find(Asset.class, assetId);
    if (asset == null) {
      return;
    }
    List<AssetGroup> groups =
        entityManager
            .createQuery(
                "SELECT g FROM AssetGroup g WHERE g.tenant.id IN :tenantIds ORDER BY g.name",
                AssetGroup.class)
            .setParameter("tenantIds", tenantIds)
            .getResultList();
    int tested = 0;
    int added = 0;
    for (AssetGroup g : groups) {
      if (io.openaev.database.model.Filters.isEmptyFilterGroup(g.getDynamicFilter())) {
        continue;
      }
      String groupNode = GraphNodeOutput.idOf(GraphNodeType.ASSET_GROUP, g.getId());
      if (slice.edges.containsKey(GraphEdgeOutput.idOf(GraphEdgeType.MEMBER_OF, here, groupNode))) {
        continue;
      }
      if (++tested > MAX_DYNAMIC_GROUPS || added >= cap) {
        slice.truncated = true;
        return;
      }
      if (assetGroupService.isAssetInDynamicGroup(asset, g)) {
        slice.node(GraphNodeType.ASSET_GROUP, g.getId(), g.getName(), null);
        slice.edge(GraphEdgeType.MEMBER_OF, here, groupNode, null);
        added++;
      }
    }
  }

  /**
   * The ids of the assets a dynamic group's filter matches, for the estate overview counts, read as
   * ids only so a large group never loads its assets as entities; empty for a static group or
   * without the asset group service.
   */
  List<String> dynamicMemberIds(AssetGroup group) {
    if (assetGroupService == null
        || io.openaev.database.model.Filters.isEmptyFilterGroup(group.getDynamicFilter())) {
      return List.of();
    }
    return List.copyOf(assetGroupService.dynamicAssetIds(group));
  }

  /** The members of an asset group through its dynamic filter, capped. */
  private void dynamicMembersOf(
      Slice slice, Collection<String> tenantIds, String groupId, String here, int cap) {
    if (assetGroupService == null) {
      return;
    }
    List<AssetGroup> found =
        entityManager
            .createQuery(
                "SELECT g FROM AssetGroup g WHERE g.id = :id AND g.tenant.id IN :tenantIds",
                AssetGroup.class)
            .setParameter("id", groupId)
            .setParameter("tenantIds", tenantIds)
            .getResultList();
    if (found.isEmpty()
        || io.openaev.database.model.Filters.isEmptyFilterGroup(found.get(0).getDynamicFilter())) {
      return;
    }
    AssetGroup group = assetGroupService.computeDynamicAssets(found.get(0));
    int added = 0;
    for (Asset a : group.getDynamicAssets()) {
      String assetNode = GraphNodeOutput.idOf(GraphNodeType.ASSET, a.getId());
      if (slice.edges.containsKey(GraphEdgeOutput.idOf(GraphEdgeType.MEMBER_OF, here, assetNode))) {
        continue;
      }
      if (added >= cap) {
        slice.truncated = true;
        return;
      }
      slice.node(GraphNodeType.ASSET, a.getId(), a.getName(), a.getType());
      slice.edge(GraphEdgeType.MEMBER_OF, here, assetNode, null);
      added++;
    }
  }

  /**
   * A person is part of the scope when one of its identity assets, organizations or teams is: users
   * are platform-wide, so the tenant comes from what links them to the estate.
   */
  private String personNode(Slice slice, Collection<String> tenantIds, String id) {
    return single(
        slice,
        "SELECT u.id, u.firstname, u.lastname, u.email FROM User u WHERE u.id = :id AND ("
            + " EXISTS (SELECT 1 FROM Asset a WHERE a.linkedPerson = u.id"
            + " AND a.tenant.id IN :tenantIds)"
            + " OR EXISTS (SELECT 1 FROM Organization o JOIN o.users ou WHERE ou.id = u.id"
            + " AND o.tenant.id IN :tenantIds)"
            + " OR EXISTS (SELECT 1 FROM Team t JOIN t.users tu WHERE tu.id = u.id"
            + " AND t.tenant.id IN :tenantIds))",
        tenantIds,
        id,
        r ->
            slice.node(
                GraphNodeType.PERSON, (String) r[0], personLabel(r[0], r[1], r[2], r[3]), null));
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
