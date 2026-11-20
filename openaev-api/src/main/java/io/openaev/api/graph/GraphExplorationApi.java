package io.openaev.api.graph;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphOverviewOutput;
import io.openaev.api.graph.dto.GraphStartingPointsOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.utils.TxCtxScopeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exploration graph endpoints (#9388): lazy slices of the asset / asset group / finding / inject /
 * simulation / scenario graph. No endpoint returns the whole graph: a slice is one entity's ring, a
 * path between two entities, or the risk-first landing view, each capped.
 *
 * <p>Feature flag: every endpoint answers a 404 while {@code EXPLORATION_GRAPH} is off (the
 * default), see {@link GraphFeature}.
 *
 * <p>Access: the endpoint capability is ASSET READ (the graph is an exploration of the exposure
 * surface); every simulation, scenario and inject node is then kept only when the caller can read
 * it, and every finding node only when the caller reads findings, evaluated in the tenant scope of
 * the request. The routes exist under the tenant prefix only. Tenant isolation is an explicit
 * predicate in every read of {@link GraphExplorationService} plus the {@link TxCtx} scope.
 */
@RestController
@RequirePreviewFeature(PreviewFeature.EXPLORATION_GRAPH)
@RequiredArgsConstructor
@RequestMapping(GraphExplorationApi.GRAPH_URI)
@Tag(name = "Exploration graph", description = "Lazy slices of the exposure graph of a tenant")
public class GraphExplorationApi extends RestBehavior {

  public static final String GRAPH_URI = TENANT_PREFIX + "/graph";

  private final GraphExplorationService graphService;
  private final GraphLandingService landingService;
  private final UserService userService;
  private final PermissionService permissionService;

  /**
   * The top exposure story of the landing (#9447): one connected subgraph of about 30 nodes, the
   * worst attack path, else the most exposed asset. Empty when nothing failed.
   */
  @LogExecutionTime
  @GetMapping("/story")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(summary = "The top exposure story of the graph landing")
  public GraphOutput story(TxCtx ctx) {
    return landingService.story(scope(ctx), visibility(ctx));
  }

  /** The aggregated estate overview of the landing (#9447): asset-group bubbles, not nodes. */
  @LogExecutionTime
  @GetMapping("/overview")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(summary = "The aggregated estate overview of the graph landing")
  public GraphOverviewOutput overview(TxCtx ctx) {
    return landingService.overview(scope(ctx), visibility(ctx));
  }

  /** The starting-points rail of the landing (#9447). */
  @LogExecutionTime
  @GetMapping("/starting-points")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(summary = "The starting points of the graph landing")
  public GraphStartingPointsOutput startingPoints(TxCtx ctx) {
    return landingService.startingPoints(scope(ctx), visibility(ctx));
  }

  /** The risk-first landing view of #9388: exposure hot paths (kept for compatibility). */
  @LogExecutionTime
  @GetMapping("/hot-paths")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(summary = "The exposure hot paths of the tenant")
  public GraphOutput hotPaths(TxCtx ctx, @RequestParam(defaultValue = "15") int limit) {
    return graphService.hotPaths(scope(ctx), limit, visibility(ctx));
  }

  /**
   * One entity and its first ring. {@code edges} narrows the relations expanded (comma-separated
   * {@link GraphEdgeType}); empty means all. {@code limit} caps each relation (at most {@value
   * GraphExplorationService#MAX_LIMIT}); {@code graph_truncated} says when more exists.
   */
  @LogExecutionTime
  @GetMapping("/{entityType}/{entityId}/neighbourhood")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(summary = "One entity of the graph and its first ring")
  public GraphOutput neighbourhood(
      TxCtx ctx,
      @PathVariable String entityType,
      @PathVariable String entityId,
      @RequestParam(required = false) List<String> edges,
      @RequestParam(defaultValue = "50") int limit) {
    return graphService.neighbourhood(
        scope(ctx), type(entityType), entityId, edgeTypes(edges), limit, visibility(ctx));
  }

  /** A shortest path between two entities, bounded; an empty path when none is found. */
  @LogExecutionTime
  @GetMapping("/path")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(summary = "A shortest path between two entities of the graph")
  public GraphOutput path(
      TxCtx ctx,
      @RequestParam String fromType,
      @RequestParam String fromId,
      @RequestParam String toType,
      @RequestParam String toId) {
    return graphService.path(
        scope(ctx), type(fromType), fromId, type(toType), toId, visibility(ctx));
  }

  // -- helpers --

  private static Set<String> scope(TxCtx ctx) {
    return TxCtxScopeUtils.requestReadScope(ctx);
  }

  private static GraphNodeType type(String value) {
    try {
      return GraphNodeType.fromParam(value);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity type " + value);
    }
  }

  private static Set<GraphEdgeType> edgeTypes(List<String> values) {
    Set<GraphEdgeType> types = EnumSet.noneOf(GraphEdgeType.class);
    if (values == null) {
      return types;
    }
    for (String v : values) {
      if (v == null || v.isBlank()) {
        continue;
      }
      try {
        types.add(GraphEdgeType.valueOf(v.trim().toUpperCase(java.util.Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown edge type " + v);
      }
    }
    return types;
  }

  /** The grant gate of the request's nodes, in every tenant of its scope. */
  private GraphVisibility visibility(TxCtx ctx) {
    User user = userService.currentUser();
    return new GraphVisibility() {
      @Override
      public boolean canReadSimulation(String simulationId) {
        return can(simulationId, ResourceType.SIMULATION);
      }

      @Override
      public boolean canReadScenario(String scenarioId) {
        return can(scenarioId, ResourceType.SCENARIO);
      }

      @Override
      public boolean canReadAtomicTesting(String injectId) {
        return can(injectId, ResourceType.ATOMIC_TESTING);
      }

      @Override
      public boolean canReadPeople(GraphNodeType type) {
        return switch (type) {
          case PERSON -> can(null, ResourceType.PLAYER);
          case ORGANIZATION -> can(null, ResourceType.ORGANIZATION);
          case TEAM -> can(null, ResourceType.TEAM);
          default -> true;
        };
      }

      @Override
      public boolean canReadFindings() {
        return can(null, ResourceType.FINDING);
      }

      private boolean can(String id, ResourceType type) {
        return permissionService.holdsInScope(
            ctx,
            () -> permissionService.hasPermission(user, Optional.empty(), id, type, Action.READ));
      }
    };
  }
}
