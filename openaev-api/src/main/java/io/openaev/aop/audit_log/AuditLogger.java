package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.SessionManager;
import io.openaev.config.ShutdownService;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import io.openaev.utils.log.LogUtils;
import io.openaev.utils.object.ObjectDiffUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Audit logger that submits audit events asynchronously to the {@code taskLoggerExecutor} thread
 * pool. When halt-on-failure is enabled, the calling thread blocks until the audit completes; if
 * the transport failed, {@link AuditLogFailureException} is thrown on the caller's thread so it can
 * propagate through the transaction boundary and trigger a rollback.
 *
 * <p>Three public entry points:
 *
 * <ul>
 *   <li>{@link #logEvent(AuditEvent)} — generic entry point for any event type
 *   <li>{@link #logAuthEvent} — convenience for authentication events
 *   <li>{@link #logAccessControlEvent} — convenience for CRUD mutation events
 * </ul>
 *
 * <p>Both convenience methods build an {@link AuditEvent} internally and delegate to {@link
 * #logEvent(AuditEvent)}.
 */
@Component
@ConditionalOnExpression("!'${openaev.audit-logs.transports:}'.isEmpty()")
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

  private final ShutdownService shutdownService;
  private final AuditLogProperties auditLogProperties;
  private final LogService logService;
  private final ObjectMapper objectMapper;
  private final @Qualifier("taskLoggerExecutor") Executor taskLoggerExecutor;

  public boolean isAuditLoggingEnabled() {
    return logService.isEnabled();
  }

  public boolean isAuditLoggingValid(Action action) {
    return !shouldSkip(action);
  }

  // -- PREPARE LOG FAILURE --

  /**
   * Called when an audit transport fails. When halt-on-failure is disabled, logs a warning and
   * returns. When enabled, schedules application shutdown and throws {@link
   * AuditLogFailureException}.
   */
  public void prepareLogFailure() {
    if (!auditLogProperties.isHaltOnFailure()) {
      log.info("[AUDIT] Halt-on-failure disabled, application continues running...");
      return;
    }

    log.error("[AUDIT] Halt-on-failure triggered — rolling back and shutting down.");

    // Schedule application shutdown on a separate thread so the current transaction
    // can rollback first (the throw below unwinds the call stack before the shutdown runs).
    shutdownService.initiateShutdown();

    throw new AuditLogFailureException(
        "Audit transport failed with halt-on-failure enabled — transaction rolled back.");
  }

  // -- GENERIC EVENT --

  /**
   * Logs a generic audit event. Submits asynchronously to the executor pool. When halt-on-failure
   * is enabled, blocks and throws {@link AuditLogFailureException}.
   *
   * <p>The {@code logUUID} is always auto-generated internally — callers never provide one. For
   * SYSTEM-origin events, user/request metadata is omitted (no servlet context).
   */
  public void logEvent(AuditEvent event) {
    if (!isAuditLoggingEnabled()) return;

    CompletableFuture<Boolean> future =
        CompletableFuture.supplyAsync(() -> doLogEvent(event), taskLoggerExecutor);

    awaitIfHaltOnFailure(future);
  }

  /** Internal: performs the generic audit log on the executor thread. */
  private boolean doLogEvent(AuditEvent event) {
    String logUUID = UUID.randomUUID().toString();
    boolean status = false;
    try {
      status = logService.logGenericEvent(event, Level.WARNING, logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit logging failed: {}", e.getMessage(), e);
    }

    if (!status) {
      log.warn("[AUDIT] Failed to log event {}.{}", event.getEventType(), event.getEventScope());
      prepareLogFailure();
    }
    return status;
  }

  // -- AUTH EVENTS --

  /**
   * Logs an authentication event with request context data captured from outside the normal servlet
   * flow (e.g. logout handler). Restores the captured context on the executor thread before
   * performing the audit. Halt-on-failure semantics are identical to {@link #logAuthEvent}.
   */
  public void logAuthEventWithRequestContext(
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData rcd,
      AuditEventScope eventScope,
      EventStatus eventStatus,
      String provider,
      String reason) {

    if (!isAuditLoggingEnabled()) return;

    AuditEvent event = buildAuthAuditEvent(eventScope, eventStatus, provider, reason);

    CompletableFuture<Boolean> future =
        CompletableFuture.supplyAsync(
            () -> {
              if (rcd != null) {
                ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.setRequestContextData(rcd);
              }
              return doLogEvent(event);
            },
            taskLoggerExecutor);

    awaitIfHaltOnFailure(future);
  }

  /**
   * Logs an authentication event (login success/failure, logout). Builds an {@link AuditEvent}
   * internally and delegates to {@link #logEvent(AuditEvent)}.
   */
  public void logAuthEvent(
      AuditEventScope eventScope, EventStatus eventStatus, String provider, String reason) {

    if (!isAuditLoggingEnabled()) return;

    logEvent(buildAuthAuditEvent(eventScope, eventStatus, provider, reason));
  }

  /** Builds an AuditEvent for authentication events. */
  private AuditEvent buildAuthAuditEvent(
      AuditEventScope eventScope, EventStatus eventStatus, String provider, String reason) {
    Map<String, Object> ctx = new LinkedHashMap<>();
    if (provider != null) ctx.put("provider", provider);
    if (reason != null) ctx.put("reason", reason);

    return AuditEvent.builder()
        .eventType(EventType.AUTHENTICATION)
        .eventScope(eventScope)
        .eventStatus(eventStatus)
        .message(
            LogUtils.buildAuthLogMessage(
                eventScope.name().toLowerCase(), eventStatus.name().toLowerCase(), provider))
        .contextData(ctx)
        .origin(AuditEventOrigin.REQUEST)
        .build();
  }

  // -- ACCESS CONTROL EVENTS --

  /**
   * Logs an access control (mutation) event. Builds the {@link AuditEvent} and computes field-level
   * diffs asynchronously on the executor thread to avoid adding latency to the request path.
   */
  public CompletableFuture<Boolean> logAccessControlEvent(
      AuditEventScope eventScope,
      EventStatus eventStatus,
      ResourceType resourceType,
      String resourceId,
      JsonNode input,
      JsonNode output,
      JsonNode signatureNode,
      Map<String, AuditLogContext.EntitySnapshot> snapshots) {

    if (!isAuditLoggingEnabled()) return CompletableFuture.completedFuture(true);

    CompletableFuture<Boolean> future =
        CompletableFuture.supplyAsync(
            () -> {
              JsonNode entityDiffsNode =
                  ObjectDiffUtils.computeEntityDiffsNode(snapshots, objectMapper);

              Map<String, Object> ctx = new LinkedHashMap<>();
              ctx.put("input", input != null ? input : NullNode.getInstance());
              ctx.put("output", output != null ? output : NullNode.getInstance());
              ctx.put("signature", signatureNode != null ? signatureNode : NullNode.getInstance());

              AuditEvent event =
                  AuditEvent.builder()
                      .eventType(EventType.MUTATION)
                      .eventScope(eventScope)
                      .eventStatus(eventStatus)
                      .resourceType(resourceType)
                      .resourceId(resourceId)
                      .entityDiffs(entityDiffsNode)
                      .contextData(ctx)
                      .origin(AuditEventOrigin.REQUEST)
                      .build();

              return doLogEvent(event);
            },
            taskLoggerExecutor);

    awaitIfHaltOnFailure(future);
    return future;
  }

  // -- OPTIONS --

  private boolean shouldSkip(Action action) {
    return switch (action) {
      case CREATE, WRITE, DELETE, LAUNCH, DUPLICATE -> false;
      // READ/SEARCH are never audited on success — only unauthorized attempts are logged
      // (captured separately via logAuthEvent when RBAC denies access).
      case READ, SEARCH -> true;
      default -> true; // SKIP_RBAC, PROCESS
    };
  }

  /**
   * When halt-on-failure is enabled, blocks on the audit future. If the audit task failed for any
   * reason, invalidates the current HTTP session and throws {@link AuditLogFailureException} so the
   * surrounding transaction rolls back. This guarantees the "no commit without successful audit"
   * invariant even if the failure is unexpected (e.g. a runtime exception escaping the async task).
   */
  private void awaitIfHaltOnFailure(CompletableFuture<Boolean> auditFuture) {
    if (!auditLogProperties.isHaltOnFailure()) {
      return;
    }
    try {
      auditFuture.join();
    } catch (CompletionException ex) {
      SessionManager.invalidateCurrentSession();
      if (ex.getCause() instanceof AuditLogFailureException auditEx) {
        throw auditEx;
      }
      // Unexpected failure — still must rollback to honour halt-on-failure guarantee
      log.error("[AUDIT] Unexpected error during halt-on-failure audit — forcing rollback", ex);
      throw new AuditLogFailureException(
          "Audit task failed unexpectedly with halt-on-failure enabled — transaction rolled back.",
          ex.getCause());
    }
  }
}
