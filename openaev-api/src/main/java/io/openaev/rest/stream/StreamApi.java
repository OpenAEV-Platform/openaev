package io.openaev.rest.stream;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.service.StreamService.X_ACCEL_BUFFERING;

import io.openaev.annotation.NoTenantScope;
import io.openaev.aop.AccessControl;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import reactor.core.publisher.Flux;

@RestController
@Slf4j
@RequiredArgsConstructor
@EnableAsync
public class StreamApi extends RestBehavior {
  private final StreamService streamService;

  /** Create a flux for current user & session */
  @GetMapping(
      path = {"/api/stream", TENANT_PREFIX + "/stream"},
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  // The stream multiplexes every event of the tenant, so no single capability can gate the
  // subscription: a subscriber legitimately needs it for any resource it is entitled to read.
  // Authorisation happens per event instead, in listenDatabaseUpdate, which resolves the resource
  // governing each event and checks it against the consumer before the event is sent.
  @AccessControl(skipRBAC = true)
  @NoTenantScope
  // No TxCtx here on purpose: propagation = NEVER guarantees no transaction is ever active for
  // this method, so TenantScopeTransactionAspect's set_config(..., true) would have no
  // transaction to scope (a silent no-op at best). The method also touches no v2-active table -
  // it only merges in-memory event fluxes - so there is nothing here that needs scoping.
  @Transactional(
      propagation = Propagation.NEVER) // Don't start a transaction for the stream, it will be async
  public ResponseEntity<Flux<Object>> streamFlux() {
    String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();
    // Build the database event flux.
    Flux<Object> dataFlux = streamService.makeDataFlux(sessionId);
    // Build the health check flux.
    Flux<Object> ping = streamService.makePingFlux();
    // Merge the 2 flux to create the final one.
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header(X_ACCEL_BUFFERING, "no")
        .body(Flux.merge(dataFlux, ping));
  }
}
