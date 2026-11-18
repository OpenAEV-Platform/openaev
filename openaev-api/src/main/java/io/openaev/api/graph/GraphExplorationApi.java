package io.openaev.api.graph;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.utils.TxCtxScopeUtils;
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
 * <p>Access: the endpoint capability is ASSET READ (the graph is an exploration of the exposure
 * surface); every simulation, scenario and inject node is then kept only when the caller can read
 * it, evaluated in the tenant scope of the request. Tenant isolation is an explicit predicate in
 * every read of {@link GraphExplorationService} plus the {@link TxCtx} scope.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({GraphExplorationApi.GRAPH_URI, TENANT_PREFIX + "/graph"})
public class GraphExplorationApi extends RestBehavior {

  public static final String GRAPH_URI = "/api/graph";

  private final GraphExplorationService graphService;
  private final UserService userService;
  private final PermissionService permissionService;

  /** The risk-first landing view: exposure hot paths. */
  @GetMapping("/hot-paths")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  public GraphOutput hotPaths(TxCtx ctx, @RequestParam(defaultValue = "15") int limit) {
    return graphService.hotPaths(scope(ctx), limit, visibility(ctx));
  }

  /**
   * One entity and its first ring. {@code edges} narrows the relations expanded (comma-separated
   * {@link GraphEdgeType}); empty means all. {@code limit} caps each relation (at most {@value
   * GraphExplorationService#MAX_LIMIT}); {@code graph_truncated} says when more exists.
   */
  @GetMapping("/{entityType}/{entityId}/neighbourhood")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
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
  @GetMapping("/path")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
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

      private boolean can(String id, ResourceType type) {
        return permissionService.holdsInScope(
            ctx,
            () -> permissionService.hasPermission(user, Optional.empty(), id, type, Action.READ));
      }
    };
  }
}
