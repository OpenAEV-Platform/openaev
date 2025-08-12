package io.openbas.rest.stream;

import static io.openbas.config.SessionHelper.currentUser;
import static io.openbas.database.audit.ModelBaseListener.DATA_DELETE;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.aop.RBAC;
import io.openbas.config.OpenBASPrincipal;
import io.openbas.database.audit.BaseEvent;
import io.openbas.database.model.Action;
import io.openbas.database.model.User;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.PermissionService;
import io.openbas.service.UserService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RestController
@Slf4j
public class StreamApi extends RestBehavior {

  public static final String EVENT_TYPE_MESSAGE = "message";
  public static final String EVENT_TYPE_PING = "ping";
  public static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";
  private final Map<String, Tuple2<OpenBASPrincipal, FluxSink<Object>>> consumers = new HashMap<>();

  private final PermissionService permissionService;
  private final UserService userService;

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

  @EventListener
  public void listenDatabaseUpdate(BaseEvent event) {
    consumers.entrySet().stream()
        .parallel()
        .forEach(
            entry -> {
              User user = userService.user(entry.getValue().getT1().getId());
              // FIXME find a way to cache user
              // -> close session when user se login

              Tuple2<OpenBASPrincipal, FluxSink<Object>> tupleFlux = entry.getValue();
              FluxSink<Object> fluxSink = tupleFlux.getT2();
              if (!permissionService.hasPermission(
                  user,
                  event.getInstance().getId(),
                  event.getInstance().getResourceType(),
                  Action.READ)) {
                // If user as no visibility, we can send a "delete" userEvent with only the internal
                // id
                // TODO -> rethink this logic -> do we need to send DELETE events
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
                  log.warn(String.format("Class %s cant be streamed", simpleName), e);
                }
              } else {
                sendStreamEvent(fluxSink, event);
              }
            });
  }

  /** Create a flux for current user & session */
  @GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true) // TODO RBAC check must be done manually for every event in this method
  public ResponseEntity<Flux<Object>> streamFlux() {
    String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();
    // Build the database event flux.
    Flux<Object> dataFlux =
        Flux.create(
                fluxSinkConsumer ->
                    consumers.put(sessionId, Tuples.of(currentUser(), fluxSinkConsumer)))
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
