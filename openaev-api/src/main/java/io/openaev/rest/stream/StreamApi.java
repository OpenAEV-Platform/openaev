package io.openaev.rest.stream;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.openaev.database.model.UserScoped;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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
  public static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";
  private final Map<String, StreamConsumer> consumers = new HashMap<>();

  private final PermissionService permissionService;
  private final UserService userService;

  private Instant lastUpdate = Instant.now();

  private record StreamConsumer(
      OpenAEVPrincipal principal, String tenantId, FluxSink<Object> fluxSink) {}

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

          // User-scoped entities (e.g. notifications) are only delivered to their owner,
          // bypassing the capability-based masking below.
          if (event.getInstance() instanceof UserScoped userScoped) {
            if (consumer.principal().getId().equals(userScoped.getOwnerUserId())) {
              sendStreamEvent(consumer.fluxSink(), event);
            }
            return;
          }

          User user = userService.user(consumer.principal().getId());
          // FIXME find a way to cache user
          // -> close session when user se login

          // Set the tenant context for permission checks on the async thread.
          // Without this, TenantContext defaults to DEFAULT_TENANT_UUID, causing
          // tenant-scoped capabilities to be invisible and incorrect DELETE events
          // to be sent for entities on non-default tenants.
          if (consumer.tenantId() != null && !consumer.tenantId().isBlank()) {
            TenantContext.setCurrentTenant(consumer.tenantId());
          }

          try {
            FluxSink<Object> fluxSink = consumer.fluxSink();
            if (!permissionService.hasPermission(
                user,
                Optional.empty(),
                event.getInstance().getId(),
                event.getInstance().getResourceType(),
                Action.READ)) {
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
            // Reset tenant context to avoid leaking into other consumers in the loop
            if (consumer.tenantId() != null && !consumer.tenantId().isBlank()) {
              TenantContext.clearCurrentTenant();
            }
          }
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
            .doAfterTerminate(() -> consumers.remove(sessionId));
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
