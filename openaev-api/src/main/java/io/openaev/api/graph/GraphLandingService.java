package io.openaev.api.graph;

import io.openaev.api.graph.GraphExplorationService.Slice;
import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeOutput;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphOverviewGroupOutput;
import io.openaev.api.graph.dto.GraphOverviewLinkOutput;
import io.openaev.api.graph.dto.GraphOverviewOutput;
import io.openaev.api.graph.dto.GraphResult;
import io.openaev.api.graph.dto.GraphStartingPointOutput;
import io.openaev.api.graph.dto.GraphStartingPointOutput.Kind;
import io.openaev.api.graph.dto.GraphStartingPointsOutput;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Filters;
import io.openaev.expectation.ExpectationType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The landing of the exploration graph (#9447), replacing the scatter of hot-path clusters of #9388
 * by one of two views chosen by the data, plus a rail of starting points:
 *
 * <ul>
 *   <li>{@link #story}: the top exposure story, ONE connected subgraph of about {@value
 *       #STORY_BUDGET} nodes: the worst attack path found by the attack path feature (the
 *       simulation with the most failed steps the caller can read), else the most exposed asset
 *       with its failed injects, findings, groups and identity.
 *   <li>{@link #overview}: the aggregated estate, a few dozen asset-group bubbles sized by member
 *       count and coloured by failure rate, linked to the simulations that targeted them: counts,
 *       not nodes, so it holds at 100K assets.
 *   <li>{@link #startingPoints}: the rail beside the canvas.
 * </ul>
 *
 * <p>The tenant-wide aggregates go through {@link GraphLandingCache} (raw rows per tenant scope);
 * the caller's resource gate is applied after it on every request. Every read carries the explicit
 * tenant predicate of {@link GraphExplorationService}.
 */
@Service
@RequiredArgsConstructor
public class GraphLandingService {

  /** Node budget of the top exposure story. */
  static final int STORY_BUDGET = 30;

  /** Attack-path steps drawn in a story at most. */
  static final int STORY_STEPS = 12;

  /** Bubbles of the estate overview at most (the ungrouped bucket comes on top). */
  static final int OVERVIEW_GROUPS = 40;

  /** Simulations linked to one bubble at most. */
  static final int OVERVIEW_LINKS_PER_GROUP = 3;

  /** Entries per section of the starting-points rail. */
  static final int RAIL_SIZE = 5;

  /** Ranked rows read for a section, so the gate can drop some and still fill it. */
  private static final int RANKING_DEPTH = 25;

  private static final String PREVENTED = ExpectationType.PREVENTION.successLabel;

  private static final String FAILED_EXPECTATION =
      "e.score IS NOT NULL AND e.score < COALESCE(e.expectedScore, 100)";

  private static final String FAILED_STEP =
      "(x.preventionStatus IS NOT NULL OR x.detectionStatus IS NOT NULL)"
          + " AND (x.preventionStatus IS NULL OR x.preventionStatus <> :prevented)";

  @PersistenceContext private EntityManager entityManager;

  private final GraphExplorationService explorer;
  private final GraphLandingCache cache;

  /** One ranked row of an aggregate: an entity, its label, its kind and what ranked it. */
  record Ranked(String id, String label, String subtype, long score) {}

  /** The raw, ungated rows of the overview, cached per tenant scope. */
  record OverviewData(
      List<GraphOverviewGroupOutput> groups,
      List<Object[]> links,
      long assetCount,
      boolean truncated) {}

  // -- Story --

  /**
   * The top exposure story: one connected subgraph, never a scatter. Empty when the scope holds no
   * failed step and no failed expectation, which tells the UI to land on the overview instead.
   */
  public GraphOutput story(Collection<String> tenantIds, GraphVisibility visibility) {
    Predicate<GraphNodeOutput> gate = explorer.gate(tenantIds, visibility);
    for (Ranked path : attackPaths(tenantIds)) {
      if (!visibility.canReadSimulation(path.id())) {
        continue;
      }
      Slice slice = new Slice();
      explorer.steps(slice, tenantIds, "x.simulationId = :id", path.id(), STORY_STEPS);
      if (slice.edges.isEmpty()) {
        continue;
      }
      String focus = slice.edges.values().iterator().next().source();
      decorate(slice, tenantIds, Set.of(GraphEdgeType.FOUND_ON, GraphEdgeType.IDENTITY_OF));
      return slice.build(focus, gate, List.of());
    }
    for (Ranked asset : exposedAssets(tenantIds)) {
      Slice slice = new Slice();
      String focus =
          explorer.expand(
              slice,
              tenantIds,
              GraphNodeType.ASSET,
              asset.id(),
              Set.of(
                  GraphEdgeType.FOUND_ON,
                  GraphEdgeType.TARGETS,
                  GraphEdgeType.IDENTITY_OF,
                  GraphEdgeType.LINKED_TO,
                  GraphEdgeType.ATTACK_STEP),
              6);
      if (focus == null) {
        continue;
      }
      staticGroups(slice, tenantIds, asset.id(), focus, 3);
      GraphOutput out = slice.build(focus, gate, List.of());
      if (!out.nodes().isEmpty()) {
        return out;
      }
    }
    return new GraphOutput(null, List.of(), List.of(), false, List.of());
  }

  /**
   * Adds one ring of {@code types} to every asset of the story, one entity per relation, while the
   * story stays under its budget.
   */
  private void decorate(Slice slice, Collection<String> tenantIds, Set<GraphEdgeType> types) {
    List<GraphNodeOutput> assets =
        slice.nodes.values().stream().filter(n -> n.nodeType() == GraphNodeType.ASSET).toList();
    for (GraphNodeOutput asset : assets) {
      if (slice.nodes.size() >= STORY_BUDGET) {
        slice.truncated = true;
        return;
      }
      explorer.expand(slice, tenantIds, GraphNodeType.ASSET, asset.entityId(), types, 1);
    }
  }

  /**
   * Static groups of one asset: the story names the groups without testing every dynamic filter of
   * the tenant (the asset ring does that on demand).
   */
  private void staticGroups(
      Slice slice, Collection<String> tenantIds, String assetId, String here, int cap) {
    List<Object[]> rows =
        entityManager
            .createQuery(
                "SELECT g.id, g.name FROM AssetGroup g JOIN g.assets a"
                    + " WHERE a.id = :id AND g.tenant.id IN :tenantIds ORDER BY g.name",
                Object[].class)
            .setParameter("id", assetId)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(cap + 1)
            .getResultList();
    if (rows.size() > cap) {
      slice.truncated = true;
      rows = rows.subList(0, cap);
    }
    for (Object[] r : rows) {
      String g = slice.node(GraphNodeType.ASSET_GROUP, (String) r[0], (String) r[1], null);
      slice.edge(GraphEdgeType.MEMBER_OF, here, g, null);
    }
  }

  // -- Overview --

  /** The aggregated estate: asset-group bubbles and the simulations that targeted them. */
  public GraphOverviewOutput overview(Collection<String> tenantIds, GraphVisibility visibility) {
    OverviewData data = cache.get("overview", tenantIds, () -> readOverview(tenantIds));
    Map<String, Boolean> readable = new HashMap<>();
    Map<String, Integer> perGroup = new HashMap<>();
    List<GraphOverviewLinkOutput> links = new ArrayList<>();
    for (Object[] r : data.links()) {
      String group = (String) r[0];
      String simulation = (String) r[1];
      if (perGroup.getOrDefault(group, 0) >= OVERVIEW_LINKS_PER_GROUP) {
        continue;
      }
      if (!readable.computeIfAbsent(simulation, visibility::canReadSimulation)) {
        continue;
      }
      long scored = number(r[3]);
      long failed = number(r[4]);
      GraphResult result =
          failed > 0 ? GraphResult.FAILED : scored > 0 ? GraphResult.SUCCESS : GraphResult.PENDING;
      links.add(new GraphOverviewLinkOutput(group, simulation, (String) r[2], failed, result));
      perGroup.merge(group, 1, Integer::sum);
    }
    return new GraphOverviewOutput(data.groups(), links, data.assetCount(), data.truncated());
  }

  private OverviewData readOverview(Collection<String> tenantIds) {
    List<Object[]> groupRows =
        entityManager
            .createQuery(
                "SELECT g.id, g.name, COUNT(a.id) FROM AssetGroup g LEFT JOIN g.assets a"
                    + " WHERE g.tenant.id IN :tenantIds"
                    + " GROUP BY g.id, g.name ORDER BY COUNT(a.id) DESC, g.name ASC",
                Object[].class)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(OVERVIEW_GROUPS + 1)
            .getResultList();
    boolean truncated = groupRows.size() > OVERVIEW_GROUPS;
    if (truncated) {
      groupRows = groupRows.subList(0, OVERVIEW_GROUPS);
    }
    List<String> ids = groupRows.stream().map(r -> (String) r[0]).toList();
    Map<String, long[]> scores = new HashMap<>();
    Set<String> dynamic = new HashSet<>();
    if (!ids.isEmpty()) {
      for (Object[] r :
          entityManager
              .createQuery(
                  "SELECT g.id, COUNT(e),"
                      + " SUM(CASE WHEN e.score < COALESCE(e.expectedScore, 100) THEN 1 ELSE 0 END)"
                      + " FROM TechnicalInjectExpectation e JOIN e.asset a, AssetGroup g"
                      + " JOIN g.assets ga"
                      + " WHERE ga.id = a.id AND g.id IN :ids AND e.score IS NOT NULL"
                      + " AND a.tenant.id IN :tenantIds GROUP BY g.id",
                  Object[].class)
              .setParameter("ids", ids)
              .setParameter("tenantIds", tenantIds)
              .getResultList()) {
        scores.put((String) r[0], new long[] {number(r[1]), number(r[2])});
      }
      for (AssetGroup g :
          entityManager
              .createQuery("SELECT g FROM AssetGroup g WHERE g.id IN :ids", AssetGroup.class)
              .setParameter("ids", ids)
              .getResultList()) {
        if (!Filters.isEmptyFilterGroup(g.getDynamicFilter())) {
          dynamic.add(g.getId());
        }
      }
    }
    List<GraphOverviewGroupOutput> groups = new ArrayList<>();
    for (Object[] r : groupRows) {
      String id = (String) r[0];
      long[] s = scores.getOrDefault(id, new long[] {0, 0});
      groups.add(
          new GraphOverviewGroupOutput(
              id,
              (String) r[1],
              number(r[2]),
              s[0],
              s[1],
              GraphOverviewGroupOutput.rate(s[1], s[0]),
              dynamic.contains(id),
              false));
    }
    long assetCount =
        number(
            entityManager
                .createQuery(
                    "SELECT COUNT(a.id) FROM Asset a WHERE a.tenant.id IN :tenantIds", Long.class)
                .setParameter("tenantIds", tenantIds)
                .getSingleResult());
    long grouped =
        number(
            entityManager
                .createQuery(
                    "SELECT COUNT(DISTINCT a.id) FROM AssetGroup g JOIN g.assets a"
                        + " WHERE g.tenant.id IN :tenantIds AND a.tenant.id IN :tenantIds",
                    Long.class)
                .setParameter("tenantIds", tenantIds)
                .getSingleResult());
    long ungrouped = Math.max(assetCount - grouped, 0);
    if (ungrouped > 0) {
      Object[] u =
          entityManager
              .createQuery(
                  "SELECT COUNT(e),"
                      + " SUM(CASE WHEN e.score < COALESCE(e.expectedScore, 100) THEN 1 ELSE 0 END)"
                      + " FROM TechnicalInjectExpectation e JOIN e.asset a"
                      + " WHERE a.tenant.id IN :tenantIds AND e.score IS NOT NULL"
                      + " AND NOT EXISTS (SELECT 1 FROM AssetGroup g JOIN g.assets ga"
                      + " WHERE ga.id = a.id)",
                  Object[].class)
              .setParameter("tenantIds", tenantIds)
              .getSingleResult();
      long scored = number(u[0]);
      long failed = number(u[1]);
      groups.add(
          new GraphOverviewGroupOutput(
              GraphOverviewGroupOutput.UNGROUPED_ID,
              GraphOverviewGroupOutput.UNGROUPED_ID,
              ungrouped,
              scored,
              failed,
              GraphOverviewGroupOutput.rate(failed, scored),
              false,
              true));
    }
    List<Object[]> links =
        ids.isEmpty()
            ? List.of()
            : entityManager
                .createQuery(
                    "SELECT g.id, x.id, x.name,"
                        + " SUM(CASE WHEN e.score IS NOT NULL THEN 1 ELSE 0 END),"
                        + " SUM(CASE WHEN e.score IS NOT NULL"
                        + " AND e.score < COALESCE(e.expectedScore, 100) THEN 1 ELSE 0 END)"
                        + " FROM TechnicalInjectExpectation e JOIN e.assetGroup g"
                        + " JOIN e.inject i JOIN i.exercise x"
                        + " WHERE g.id IN :ids AND x.tenant.id IN :tenantIds"
                        + " GROUP BY g.id, x.id, x.name"
                        + " ORDER BY SUM(CASE WHEN e.score IS NOT NULL"
                        + " AND e.score < COALESCE(e.expectedScore, 100) THEN 1 ELSE 0 END) DESC,"
                        + " x.name ASC",
                    Object[].class)
                .setParameter("ids", ids)
                .setParameter("tenantIds", tenantIds)
                .setMaxResults(OVERVIEW_GROUPS * OVERVIEW_LINKS_PER_GROUP * 2)
                .getResultList();
    return new OverviewData(List.copyOf(groups), List.copyOf(links), assetCount, truncated);
  }

  // -- Starting points --

  /** The rail: every section ranked, gated, at most {@value #RAIL_SIZE} entries each. */
  public GraphStartingPointsOutput startingPoints(
      Collection<String> tenantIds, GraphVisibility visibility) {
    List<GraphStartingPointOutput> points = new ArrayList<>();
    add(points, Kind.EXPOSED_ASSET, GraphNodeType.ASSET, exposedAssets(tenantIds), r -> true);
    List<Ranked> failingGroups =
        overview(tenantIds, visibility).groups().stream()
            .filter(g -> !g.ungrouped() && g.failedCount() > 0)
            .sorted((a, b) -> Long.compare(b.failedCount(), a.failedCount()))
            .map(g -> new Ranked(g.groupId(), g.groupName(), null, g.failedCount()))
            .toList();
    add(points, Kind.FAILING_GROUP, GraphNodeType.ASSET_GROUP, failingGroups, r -> true);
    List<Ranked> latest =
        entityManager
            .createQuery(
                "SELECT x.id, x.name FROM Exercise x WHERE x.tenant.id IN :tenantIds"
                    + " ORDER BY x.start DESC NULLS LAST, x.name ASC",
                Object[].class)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(RANKING_DEPTH)
            .getResultList()
            .stream()
            .map(r -> new Ranked((String) r[0], (String) r[1], null, 0))
            .filter(r -> visibility.canReadSimulation(r.id()))
            .limit(1)
            .toList();
    add(points, Kind.LATEST_SIMULATION, GraphNodeType.SIMULATION, latest, r -> true);
    add(points, Kind.TOP_FINDING, GraphNodeType.FINDING, topFindings(tenantIds), r -> true);
    add(
        points,
        Kind.ATTACK_PATH,
        GraphNodeType.SIMULATION,
        attackPaths(tenantIds),
        r -> visibility.canReadSimulation(r.id()));
    if (visibility.canReadPeople(GraphNodeType.PERSON)) {
      add(points, Kind.EXPOSED_PERSON, GraphNodeType.PERSON, exposedPersons(tenantIds), r -> true);
    }
    return new GraphStartingPointsOutput(points);
  }

  private static void add(
      List<GraphStartingPointOutput> points,
      Kind kind,
      GraphNodeType type,
      List<Ranked> ranked,
      Predicate<Ranked> visible) {
    ranked.stream()
        .filter(visible)
        .limit(RAIL_SIZE)
        .forEach(
            r ->
                points.add(
                    new GraphStartingPointOutput(
                        kind, type, r.id(), r.label() == null ? r.id() : r.label(), r.score())));
  }

  // -- Rankings (cached, ungated) --

  /** Assets by failed expectations, the ranking the hot paths of #9388 used. */
  List<Ranked> exposedAssets(Collection<String> tenantIds) {
    return cache.get(
        "exposed-assets",
        tenantIds,
        () ->
            ranked(
                "SELECT a.id, a.name, a.type, COUNT(e) FROM TechnicalInjectExpectation e"
                    + " JOIN e.asset a JOIN e.inject i"
                    + " WHERE a.tenant.id IN :tenantIds AND i.tenant.id IN :tenantIds AND "
                    + FAILED_EXPECTATION
                    + " GROUP BY a.id, a.name, a.type ORDER BY COUNT(e) DESC, a.name ASC",
                tenantIds,
                Map.of()));
  }

  /** Simulations by failed attack-path steps between two assets of the estate. */
  List<Ranked> attackPaths(Collection<String> tenantIds) {
    return cache.get(
        "attack-paths",
        tenantIds,
        () ->
            ranked(
                "SELECT x.simulationId, s.name, s.name, COUNT(x)"
                    + " FROM AttackPathExecution x, Exercise s"
                    + " WHERE s.id = x.simulationId AND x.tenant.id IN :tenantIds"
                    + " AND s.tenant.id IN :tenantIds"
                    + " AND x.sourceAssetId IS NOT NULL AND x.targetAssetId IS NOT NULL AND "
                    + FAILED_STEP
                    + " GROUP BY x.simulationId, s.name ORDER BY COUNT(x) DESC, s.name ASC",
                tenantIds,
                Map.of("prevented", PREVENTED)));
  }

  /** Findings by the number of assets they were observed on. */
  List<Ranked> topFindings(Collection<String> tenantIds) {
    return cache.get(
        "top-findings",
        tenantIds,
        () ->
            ranked(
                "SELECT f.id, f.valueDisplay, f.type, COUNT(a.id)"
                    + " FROM Finding f JOIN f.assets a"
                    + " WHERE f.tenant.id IN :tenantIds AND a.tenant.id IN :tenantIds"
                    + " GROUP BY f.id, f.valueDisplay, f.type ORDER BY COUNT(a.id) DESC, f.id ASC",
                tenantIds,
                Map.of()));
  }

  /** Persons by the failed expectations on the identity assets linked to them. */
  List<Ranked> exposedPersons(Collection<String> tenantIds) {
    return cache.get(
        "exposed-persons",
        tenantIds,
        () -> {
          List<Ranked> out = new ArrayList<>();
          for (Object[] r :
              entityManager
                  .createQuery(
                      "SELECT u.id, u.firstname, u.lastname, u.email, COUNT(e)"
                          + " FROM TechnicalInjectExpectation e JOIN e.asset a, User u"
                          + " WHERE u.id = a.linkedPerson AND a.tenant.id IN :tenantIds AND "
                          + FAILED_EXPECTATION
                          + " GROUP BY u.id, u.firstname, u.lastname, u.email"
                          + " ORDER BY COUNT(e) DESC, u.email ASC",
                      Object[].class)
                  .setParameter("tenantIds", tenantIds)
                  .setMaxResults(RANKING_DEPTH)
                  .getResultList()) {
            out.add(
                new Ranked(
                    (String) r[0],
                    GraphExplorationService.personLabel(r[0], r[1], r[2], r[3]),
                    null,
                    number(r[4])));
          }
          return List.copyOf(out);
        });
  }

  private List<Ranked> ranked(
      String jpql, Collection<String> tenantIds, Map<String, Object> parameters) {
    var query =
        entityManager
            .createQuery(jpql, Object[].class)
            .setParameter("tenantIds", tenantIds)
            .setMaxResults(RANKING_DEPTH);
    parameters.forEach(query::setParameter);
    Map<String, Ranked> out = new LinkedHashMap<>();
    for (Object[] r : query.getResultList()) {
      String subtype = r[2] == null ? null : r[2].toString();
      out.putIfAbsent(
          (String) r[0], new Ranked((String) r[0], (String) r[1], subtype, number(r[3])));
    }
    return List.copyOf(out.values());
  }

  private static long number(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }
}
