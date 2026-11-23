package io.openaev.api.graph;

import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeOutput;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The relation counts of the nodes of a slice: per node and per relation type, how many relations
 * the entity has in the data, drawn or not, so a node shows what an expansion would add (OpenCTI's
 * number on each node).
 *
 * <p>One grouped query per node type and relation over the ids of the slice, never one per node,
 * each with the same {@code tenant.id IN :tenantIds} predicate as the expansion. A count is not
 * gated entity by entity, so a relation whose other end the caller may not read as a whole is left
 * out: people and findings follow their capability, and grant-managed ends (injects, simulations,
 * scenarios, attack-path steps) are counted only for a caller who reads every grant ({@link
 * GraphVisibility#countsGrantManaged()}). Asset group membership counts the static members; a
 * dynamic group's filter is resolved on expansion only.
 */
@Component
public class GraphRelationCounter {

  /**
   * One count query: its relation, the type of its far end (null: read through the node itself, as
   * the injects of a simulation are), and its JPQL.
   */
  record CountQuery(GraphEdgeType relation, GraphNodeType farEnd, String jpql) {}

  private static final String T = " IN :tenantIds";

  static final Map<GraphNodeType, List<CountQuery>> QUERIES = new EnumMap<>(GraphNodeType.class);

  static {
    QUERIES.put(
        GraphNodeType.ASSET,
        List.of(
            new CountQuery(
                GraphEdgeType.MEMBER_OF,
                GraphNodeType.ASSET_GROUP,
                "SELECT a.id, COUNT(DISTINCT g.id) FROM AssetGroup g JOIN g.assets a"
                    + " WHERE a.id IN :ids AND g.tenant.id"
                    + T
                    + " GROUP BY a.id"),
            new CountQuery(
                GraphEdgeType.IDENTITY_OF,
                GraphNodeType.PERSON,
                "SELECT a.id, COUNT(u.id) FROM Asset a, User u"
                    + " WHERE a.id IN :ids AND u.id = a.linkedPerson AND a.tenant.id"
                    + T
                    + " GROUP BY a.id"),
            new CountQuery(
                GraphEdgeType.LINKED_TO,
                GraphNodeType.ASSET,
                "SELECT l.assetId, COUNT(DISTINCT b.id) FROM AssetLink l, Asset b"
                    + " WHERE l.assetId IN :ids AND b.id = l.linkedAssetId AND b.tenant.id"
                    + T
                    + " GROUP BY l.assetId"),
            new CountQuery(
                GraphEdgeType.LINKED_TO,
                GraphNodeType.ASSET,
                "SELECT l.linkedAssetId, COUNT(DISTINCT b.id) FROM AssetLink l, Asset b"
                    + " WHERE l.linkedAssetId IN :ids AND b.id = l.assetId AND b.tenant.id"
                    + T
                    + " GROUP BY l.linkedAssetId"),
            new CountQuery(
                GraphEdgeType.ATTACK_STEP,
                GraphNodeType.SIMULATION,
                "SELECT x.sourceAssetId, COUNT(DISTINCT ta.id) FROM AttackPathExecution x, Asset ta"
                    + " WHERE x.sourceAssetId IN :ids AND ta.id = x.targetAssetId"
                    + " AND ta.id <> x.sourceAssetId AND x.tenant.id"
                    + T
                    + " AND ta.tenant.id"
                    + T
                    + " GROUP BY x.sourceAssetId"),
            new CountQuery(
                GraphEdgeType.ATTACK_STEP,
                GraphNodeType.SIMULATION,
                "SELECT x.targetAssetId, COUNT(DISTINCT sa.id) FROM AttackPathExecution x, Asset sa"
                    + " WHERE x.targetAssetId IN :ids AND sa.id = x.sourceAssetId"
                    + " AND sa.id <> x.targetAssetId AND x.tenant.id"
                    + T
                    + " AND sa.tenant.id"
                    + T
                    + " GROUP BY x.targetAssetId"),
            new CountQuery(
                GraphEdgeType.FOUND_ON,
                GraphNodeType.FINDING,
                "SELECT a.id, COUNT(DISTINCT f.id) FROM Finding f JOIN f.assets a"
                    + " WHERE a.id IN :ids AND f.tenant.id"
                    + T
                    + " GROUP BY a.id"),
            new CountQuery(
                GraphEdgeType.TARGETS,
                GraphNodeType.INJECT,
                "SELECT a.id, COUNT(DISTINCT i.id) FROM TechnicalInjectExpectation e"
                    + " JOIN e.inject i JOIN e.asset a WHERE a.id IN :ids AND i.tenant.id"
                    + T
                    + " GROUP BY a.id")));
    QUERIES.put(
        GraphNodeType.ASSET_GROUP,
        List.of(
            new CountQuery(
                GraphEdgeType.MEMBER_OF,
                GraphNodeType.ASSET,
                "SELECT g.id, COUNT(DISTINCT a.id) FROM AssetGroup g JOIN g.assets a"
                    + " WHERE g.id IN :ids AND a.tenant.id"
                    + T
                    + " GROUP BY g.id"),
            new CountQuery(
                GraphEdgeType.TARGETS,
                GraphNodeType.INJECT,
                "SELECT g.id, COUNT(DISTINCT i.id) FROM TechnicalInjectExpectation e"
                    + " JOIN e.inject i JOIN e.assetGroup g WHERE g.id IN :ids AND i.tenant.id"
                    + T
                    + " GROUP BY g.id")));
    QUERIES.put(
        GraphNodeType.FINDING,
        List.of(
            new CountQuery(
                GraphEdgeType.FOUND_ON,
                GraphNodeType.ASSET,
                "SELECT f.id, COUNT(DISTINCT a.id) FROM Finding f JOIN f.assets a"
                    + " WHERE f.id IN :ids AND a.tenant.id"
                    + T
                    + " GROUP BY f.id"),
            new CountQuery(
                GraphEdgeType.PRODUCED,
                GraphNodeType.INJECT,
                "SELECT o.finding.id, COUNT(DISTINCT i.id) FROM FindingOccurrence o JOIN o.inject i"
                    + " WHERE o.finding.id IN :ids AND i.tenant.id"
                    + T
                    + " GROUP BY o.finding.id")));
    QUERIES.put(
        GraphNodeType.INJECT,
        List.of(
            new CountQuery(
                GraphEdgeType.TARGETS,
                GraphNodeType.ASSET,
                "SELECT i.id, COUNT(DISTINCT a.id) FROM TechnicalInjectExpectation e"
                    + " JOIN e.inject i JOIN e.asset a WHERE i.id IN :ids AND a.tenant.id"
                    + T
                    + " GROUP BY i.id"),
            new CountQuery(
                GraphEdgeType.TARGETS,
                GraphNodeType.ASSET_GROUP,
                "SELECT i.id, COUNT(DISTINCT g.id) FROM TechnicalInjectExpectation e"
                    + " JOIN e.inject i JOIN e.assetGroup g WHERE i.id IN :ids AND g.tenant.id"
                    + T
                    + " GROUP BY i.id"),
            new CountQuery(
                GraphEdgeType.PRODUCED,
                GraphNodeType.FINDING,
                "SELECT o.inject.id, COUNT(DISTINCT f.id) FROM FindingOccurrence o JOIN o.finding f"
                    + " WHERE o.inject.id IN :ids AND f.tenant.id"
                    + T
                    + " GROUP BY o.inject.id"),
            new CountQuery(
                GraphEdgeType.PART_OF,
                GraphNodeType.SIMULATION,
                "SELECT i.id, COUNT(x.id) FROM Inject i JOIN i.exercise x"
                    + " WHERE i.id IN :ids AND x.tenant.id"
                    + T
                    + " GROUP BY i.id")));
    QUERIES.put(
        GraphNodeType.SIMULATION,
        List.of(
            // The injects of a simulation are read through that simulation's own grant.
            new CountQuery(
                GraphEdgeType.PART_OF,
                null,
                "SELECT i.exercise.id, COUNT(i.id) FROM Inject i"
                    + " WHERE i.exercise.id IN :ids AND i.tenant.id"
                    + T
                    + " GROUP BY i.exercise.id"),
            new CountQuery(
                GraphEdgeType.RUN_OF,
                GraphNodeType.SCENARIO,
                "SELECT x.id, COUNT(s.id) FROM Exercise x JOIN x.scenario s"
                    + " WHERE x.id IN :ids AND x.tenant.id"
                    + T
                    + " GROUP BY x.id")));
    QUERIES.put(
        GraphNodeType.SCENARIO,
        List.of(
            new CountQuery(
                GraphEdgeType.RUN_OF,
                GraphNodeType.SIMULATION,
                "SELECT s.id, COUNT(x.id) FROM Exercise x JOIN x.scenario s"
                    + " WHERE s.id IN :ids AND x.tenant.id"
                    + T
                    + " GROUP BY s.id")));
    QUERIES.put(
        GraphNodeType.PERSON,
        List.of(
            new CountQuery(
                GraphEdgeType.IDENTITY_OF,
                GraphNodeType.ASSET,
                "SELECT a.linkedPerson, COUNT(a.id) FROM Asset a"
                    + " WHERE a.linkedPerson IN :ids AND a.tenant.id"
                    + T
                    + " GROUP BY a.linkedPerson"),
            new CountQuery(
                GraphEdgeType.WORKS_FOR,
                GraphNodeType.ORGANIZATION,
                "SELECT u.id, COUNT(DISTINCT o.id) FROM Organization o JOIN o.users u"
                    + " WHERE u.id IN :ids AND o.tenant.id"
                    + T
                    + " GROUP BY u.id"),
            new CountQuery(
                GraphEdgeType.IN_TEAM,
                GraphNodeType.TEAM,
                "SELECT u.id, COUNT(DISTINCT t.id) FROM Team t JOIN t.users u"
                    + " WHERE u.id IN :ids AND t.tenant.id"
                    + T
                    + " GROUP BY u.id")));
    QUERIES.put(
        GraphNodeType.ORGANIZATION,
        List.of(
            new CountQuery(
                GraphEdgeType.WORKS_FOR,
                GraphNodeType.PERSON,
                "SELECT o.id, COUNT(DISTINCT u.id) FROM Organization o JOIN o.users u"
                    + " WHERE o.id IN :ids AND o.tenant.id"
                    + T
                    + " GROUP BY o.id"),
            new CountQuery(
                GraphEdgeType.TEAM_OF,
                GraphNodeType.TEAM,
                "SELECT t.organization.id, COUNT(t.id) FROM Team t"
                    + " WHERE t.organization.id IN :ids AND t.tenant.id"
                    + T
                    + " GROUP BY t.organization.id")));
    QUERIES.put(
        GraphNodeType.TEAM,
        List.of(
            new CountQuery(
                GraphEdgeType.IN_TEAM,
                GraphNodeType.PERSON,
                "SELECT t.id, COUNT(DISTINCT u.id) FROM Team t JOIN t.users u"
                    + " WHERE t.id IN :ids AND t.tenant.id"
                    + T
                    + " GROUP BY t.id"),
            new CountQuery(
                GraphEdgeType.TEAM_OF,
                GraphNodeType.ORGANIZATION,
                "SELECT t.id, COUNT(o.id) FROM Team t JOIN t.organization o"
                    + " WHERE t.id IN :ids AND o.tenant.id"
                    + T
                    + " GROUP BY t.id")));
  }

  @PersistenceContext private EntityManager entityManager;

  /** Whether the caller may count the relations whose far end is {@code farEnd}. */
  static boolean countable(GraphNodeType farEnd, GraphVisibility visibility) {
    if (farEnd == null) {
      return true;
    }
    return switch (farEnd) {
      case PERSON, ORGANIZATION, TEAM -> visibility.canReadPeople(farEnd);
      case FINDING -> visibility.canReadFindings();
      case INJECT, SIMULATION, SCENARIO -> visibility.countsGrantManaged();
      default -> true;
    };
  }

  /** {@code out} with every node carrying its relation counts. */
  public GraphOutput counted(
      GraphOutput out, Collection<String> tenantIds, GraphVisibility visibility) {
    if (out.nodes().isEmpty()) {
      return out;
    }
    Map<GraphNodeType, List<String>> idsByType = new EnumMap<>(GraphNodeType.class);
    for (GraphNodeOutput n : out.nodes()) {
      idsByType.computeIfAbsent(n.nodeType(), k -> new ArrayList<>()).add(n.entityId());
    }
    Map<String, Map<GraphEdgeType, Long>> counts = new HashMap<>();
    idsByType.forEach(
        (type, ids) -> {
          for (CountQuery q : QUERIES.getOrDefault(type, List.of())) {
            if (!countable(q.farEnd(), visibility)) {
              continue;
            }
            List<Object[]> rows =
                entityManager
                    .createQuery(q.jpql(), Object[].class)
                    .setParameter("ids", ids)
                    .setParameter("tenantIds", tenantIds)
                    .getResultList();
            for (Object[] r : rows) {
              if (r[0] == null) {
                continue;
              }
              counts
                  .computeIfAbsent(
                      GraphNodeOutput.idOf(type, r[0].toString()),
                      k -> new EnumMap<>(GraphEdgeType.class))
                  .merge(q.relation(), ((Number) r[1]).longValue(), Long::sum);
            }
          }
        });
    List<GraphNodeOutput> nodes = new ArrayList<>(out.nodes().size());
    for (GraphNodeOutput n : out.nodes()) {
      nodes.add(n.withRelationCounts(counts.getOrDefault(n.nodeId(), Map.of())));
    }
    return new GraphOutput(out.focus(), nodes, out.edges(), out.truncated(), out.path());
  }
}
