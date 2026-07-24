package io.openaev.rest.stream;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openaev.aop.AccessControl;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Action;
import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.TenantIdBase;
import io.openaev.database.model.User;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.utils.BulkOperationMonitor;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@RestController
@Slf4j
public class StreamApi extends RestBehavior {

  public static final String EVENT_TYPE_MESSAGE = "message";
  public static final String EVENT_TYPE_PING = "ping";
  public static final String EVENT_TYPE_BULK_OPERATION = "bulk-operation";
  public static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";

  // Mutated from several threads: SSE connect (request thread), disconnect (reactor
  // doAfterTerminate) and iteration in the @Async broadcast path, so it must be concurrent.
  private final Map<String, StreamConsumer> consumers = new ConcurrentHashMap<>();

  // Short-lived per-principal user cache for the broadcast path. listenDatabaseUpdate
  // runs for EVERY database mutation and fans out to EVERY connected consumer; reloading
  // each user from the database per event drained the Hikari connection pool and hung the
  // platform (typically while viewing a running simulation, which emits many events). See
  // the resolveUser javadoc.
  private final Map<String, CachedUser> userCache = new ConcurrentHashMap<>();
  private static final Duration USER_CACHE_TTL = Duration.ofSeconds(60);

  // Short-lived per-(principal, resource) permission decisions for the broadcast path.
  // hasPermission is @Transactional and, for inject events (the most frequent mutation while a
  // simulation is running), resolves the parent permission by loading the FULL Inject entity
  // graph plus a grant query — per event, per consumer. Under a running simulation this produced
  // hundreds of queries per second, saturated Postgres and exhausted the Hikari pool, freezing
  // the whole platform (#6868). Repeated mutations of the same resource are the norm (every
  // trace/status/expectation update re-touches the same inject), so a 30s TTL absorbs almost all
  // of it; permission changes propagate to the stream within 30s, consistent with the 60s user
  // cache above. Bounded so mass disconnects / huge simulations cannot grow it unboundedly.
  private final Cache<PermissionCacheKey, Boolean> permissionDecisionCache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(100_000).build();

  private final PermissionService permissionService;
  private final UserService userService;

  private Instant lastUpdate = Instant.now();

  private record StreamConsumer(
      OpenAEVPrincipal principal, String tenantId, FluxSink<Object> fluxSink) {}

  private record CachedUser(User user, Instant fetchedAt) {}

  private record PermissionCacheKey(
      String principalId, String resourceId, ResourceType resourceType, String tenantId) {}

  /**
   * Resolves the per-event READ permission through the short-lived decision cache. Must be called
   * with the consumer's tenant context already set (the underlying check is tenant-aware). Cache
   * misses of the same key may race and both hit the database: bounded and far cheaper than one
   * resolution per event per consumer.
   */
  private boolean hasReadPermission(StreamConsumer consumer, User user, BaseEvent event) {
    PermissionCacheKey key =
        new PermissionCacheKey(
            consumer.principal().getId(),
            event.getInstance().getId(),
            event.getInstance().getResourceType(),
            consumer.tenantId());
    Boolean cached = permissionDecisionCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }
    boolean allowed =
        permissionService.hasPermission(
            user,
            Optional.empty(),
            event.getInstance().getId(),
            event.getInstance().getResourceType(),
            Action.READ);
    permissionDecisionCache.put(key, allowed);
    return allowed;
  }

  /**
   * Resolves a consumer's user for the per-event permission check without hitting the database on
   * every event.
   *
   * <p>{@code listenDatabaseUpdate} runs for every DB mutation and iterates over every connected
   * consumer. Loading each user with a transactional {@code findById} per event per consumer
   * exhausted the connection pool (total=20) under load and blocked all other requests, including
   * session lookups. Users eagerly load their capabilities/permissions, so a short-lived detached
   * copy is safe for {@link PermissionService#hasPermission}; changes are picked up within {@link
   * #USER_CACHE_TTL}.
   */
  private User resolveUser(String principalId) {
    CachedUser cached = userCache.get(principalId);
    if (cached != null && cached.fetchedAt().isAfter(Instant.now().minus(USER_CACHE_TTL))) {
      return cached.user();
    }
    // Not cached / stale: a handful of concurrent stream threads may refresh the same
    // principal at TTL expiry, which is bounded and far cheaper than one query per event.
    User user = userService.user(principalId);
    userCache.put(principalId, new CachedUser(user, Instant.now()));
    return user;
  }

  public StreamApi(PermissionService permissionService, UserService userService) {
    this.permissionService = permissionService;
    this.userService = userService;
  }

  private void sendStreamEvent(FluxSink<Object> flux, BaseEvent event) {
    // Serialize the instance now for lazy session decoupling
    event.setInstanceData(mapper.valueToTree(event.getInstance()));
    ServerSentEvent<BaseEvent> message =
        ServerSentEvent.builder(event).event(EVENT_TYPE_MESSAGE).build();
    flux.next(message);
  }

  private static final EnumSet<ResourceType> RESOURCES_STREAM_EXCLUSION =
      EnumSet.of(
          ResourceType.VULNERABILITY,
          ResourceType.PAYLOAD,
          ResourceType.THREAT_ARSENAL,
          ResourceType.CONNECTOR_INSTANCE_LOG);

  @Async("streamExecutor")
  @TransactionalEventListener
  public void listenDatabaseUpdate(BaseEvent event) {
    if (RESOURCES_STREAM_EXCLUSION.contains(event.getInstance().getResourceType())
        || !event.isListened()) {
      return;
    }
    if (lastUpdate.isBefore(Instant.now().minus(5, ChronoUnit.MINUTES))) {
      log.info(
          "There are currently {} users connected to the stream. The id of the users connected : {}",
          consumers.size(),
          consumers.values().stream()
              .map(StreamConsumer::principal)
              .map(OpenAEVPrincipal::getId)
              .collect(Collectors.joining(", ")));

      lastUpdate = Instant.now();
    }

    consumers.forEach(
        (key, consumer) -> {
          if (!isVisibleForTenant(event, consumer.tenantId())) {
            return;
          }

          // Resolved from a short-lived cache instead of a DB query per event per consumer,
          // which used to exhaust the connection pool while viewing busy simulations.
          User user = resolveUser(consumer.principal().getId());

          // Set the tenant context for permission checks on the async thread.
          // Without this, TenantContext defaults to DEFAULT_TENANT_UUID, causing
          // tenant-scoped capabilities to be invisible and incorrect DELETE events
          // to be sent for entities on non-default tenants. Legacy consumers
          // (blank tenant) are explicitly cleared so a reused @Async pool thread
          // can never evaluate (and cache) their decisions under a tenant leaked
          // by a previous task.
          if (consumer.tenantId() != null && !consumer.tenantId().isBlank()) {
            TenantContext.setCurrentTenant(consumer.tenantId());
          } else {
            TenantContext.clearCurrentTenant();
          }

          try {
            FluxSink<Object> fluxSink = consumer.fluxSink();
            if (!hasReadPermission(consumer, user, event)) {
              try {
                String propertyId =
                    event
                        .getInstance()
                        .getClass()
                        .getDeclaredField("id")
                        .getAnnotation(JsonProperty.class)
                        .value();
                ObjectNode deleteNode = mapper.createObjectNode();
                deleteNode.set(
                    propertyId, mapper.convertValue(event.getInstance().getId(), JsonNode.class));
                BaseEvent userEvent = event.clone();
                userEvent.setInstanceData(deleteNode);
                userEvent.setType(DATA_DELETE);
                sendStreamEvent(fluxSink, userEvent);
              } catch (Exception e) {
                String simpleName = event.getInstance().getClass().getSimpleName();
                log.warn(String.format("Class %s can't be streamed", simpleName), e);
              }
            } else {
              sendStreamEvent(fluxSink, event);
            }
          } finally {
            // Reset tenant context unconditionally to avoid leaking into other
            // consumers in the loop or into later tasks on this pooled thread
            TenantContext.clearCurrentTenant();
          }
        });
  }

  /**
   * Delivers massive-operation progress snapshots to the stream consumers of the user who launched
   * the operation (massive operations are per user, never shared). These aggregated events replace
   * the per-entity events suppressed during bulk operations (see {@link
   * io.openaev.context.BulkOperationContext}): the frontend renders a progress indicator from them
   * and refreshes its data once, on the terminal event. The payload carries only counts and an
   * entity label, so no per-resource permission check is needed.
   */
  @Async("streamExecutor")
  @EventListener
  public void listenBulkOperation(BulkOperationMonitor.BulkOperationEvent event) {
    BulkOperationMonitor.BulkOperation operation = event.operation();
    if (operation.userId() == null) {
      // System operations (no launching user) belong to no one's history or stream.
      return;
    }
    ServerSentEvent<BulkOperationMonitor.BulkOperation> message =
        ServerSentEvent.builder(operation).event(EVENT_TYPE_BULK_OPERATION).build();
    consumers.forEach(
        (key, consumer) -> {
          if (!operation.userId().equals(consumer.principal().getId())) {
            return;
          }
          // Defensive tenant check on top of the user scoping, mirroring isVisibleForTenant:
          // tenant-scoped consumers only see operations carrying exactly their tenant id (an
          // operation without a tenant id stays off tenant streams, so no cross-tenant
          // operational metadata can leak).
          if (consumer.tenantId() != null
              && !consumer.tenantId().isBlank()
              && !consumer.tenantId().equals(operation.tenantId())) {
            return;
          }
          consumer.fluxSink().next(message);
        });
  }

  private boolean isVisibleForTenant(BaseEvent event, String consumerTenantId) {
    // Keep legacy behavior for /api/stream consumers (no explicit tenant in the URL).
    if (consumerTenantId == null || consumerTenantId.isBlank()) {
      return true;
    }
    if (event.getInstance() instanceof TenantBase tenantScoped) {
      return consumerTenantId.equals(tenantScoped.getTenant().getId());
    }
    if (event.getInstance() instanceof DualScopeBase dualScope) {
      Tenant tenant = dualScope.getTenant();
      return tenant != null && consumerTenantId.equals(tenant.getId());
    }
    // Connectors (Collector / Injector / Executor) are TenantIdBase, not TenantBase:
    // without this branch their create/ping events leaked to every tenant's stream,
    // causing the connector card to flicker in/out (appearing only on each ping).
    if (event.getInstance() instanceof TenantIdBase tenantIdScoped) {
      return consumerTenantId.equals(tenantIdScoped.getTenantId());
    }
    return true;
  }

  /** Create a flux for current user & session */
  @GetMapping(
      path = {"/api/stream", TENANT_PREFIX + "/stream"},
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @AccessControl(
      skipRBAC = true) // TODO RBAC check must be done manually for every event in this method
  @Transactional(
      propagation = Propagation.NEVER) // Don't start a transaction for the stream, it will be async
  public ResponseEntity<Flux<Object>> streamFlux() {
    String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();
    // Build the database event flux.
    Flux<Object> dataFlux =
        Flux.create(
                fluxSinkConsumer ->
                    consumers.put(
                        sessionId,
                        new StreamConsumer(
                            currentUser(), TenantContext.getCurrentTenant(), fluxSinkConsumer)))
            .doAfterTerminate(
                () -> {
                  StreamConsumer removed = consumers.remove(sessionId);
                  // Drop the cached user once the principal has no stream left open.
                  if (removed != null
                      && consumers.values().stream()
                          .noneMatch(
                              c -> removed.principal().getId().equals(c.principal().getId()))) {
                    userCache.remove(removed.principal().getId());
                  }
                });
    // Build the health check flux.
    Flux<Object> ping =
        Flux.interval(Duration.ofSeconds(1))
            .map(
                l ->
                    ServerSentEvent.builder(now().getEpochSecond()).event(EVENT_TYPE_PING).build());
    // Merge the 2 flux to create the final one.
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header(X_ACCEL_BUFFERING, "no")
        .body(Flux.merge(dataFlux, ping));
  }
}
