package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.SessionManager;
import io.openaev.config.ShutdownService;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import io.openaev.utils.object.ObjectDiffUtils;
import java.util.Map;
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
 * <p>Callers never need to handle halt-on-failure themselves — just call {@code logAuthEvent} or
 * {@code logAccessControlEvent} and the blocking/propagation is handled internally.
 *
 * <p>Phase 1: delegates to {@link LogService} for console-only output.
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

  // -- AUTH EVENTS --

  /**
   * Logs an authentication event with request context data captured from outside the normal servlet
   * flow (e.g. logout handler). Restores the captured context on the executor thread before
   * performing the audit. Halt-on-failure semantics are identical to {@link #logAuthEvent}.
   */
  public void logAuthEventWithRequestContext(
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData rcd,
      String eventScope,
      String eventStatus,
      String provider,
      String reason,
      String logUUID) {

    if (!isAuditLoggingEnabled()) return;

    CompletableFuture<Boolean> future =
        CompletableFuture.supplyAsync(
            () -> {
              if (rcd != null) {
                ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.setRequestContextData(rcd);
              }
              return doLogAuthEvent(eventScope, eventStatus, provider, reason, logUUID);
            },
            taskLoggerExecutor);

    awaitIfHaltOnFailure(future);
  }

  /**
   * Logs an authentication event (login success/failure, logout). The audit is submitted
   * asynchronously. When halt-on-failure is enabled, blocks and rethrows {@link
   * AuditLogFailureException} on the caller's thread.
   */
  public void logAuthEvent(
      String eventScope, String eventStatus, String provider, String reason, String logUUID) {

    if (!isAuditLoggingEnabled()) return;

    CompletableFuture<Boolean> future =
        CompletableFuture.supplyAsync(
            () -> doLogAuthEvent(eventScope, eventStatus, provider, reason, logUUID),
            taskLoggerExecutor);

    awaitIfHaltOnFailure(future);
  }

  /** Internal: performs the auth audit log on the executor thread. */
  private boolean doLogAuthEvent(
      String eventScope, String eventStatus, String provider, String reason, String logUUID) {
    boolean status = false;
    try {
      status =
          logService.logAuthEvent(
              eventScope, eventStatus, provider, reason, Level.WARNING, logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit auth logging failed: {}", e.getMessage(), e);
    }

    if (!status) {
      log.warn("[AUDIT] Failed to log auth event for {}.{}", provider, eventScope);
      prepareLogFailure();
    }
    return status;
  }

  // -- ACCESS CONTROL EVENTS --

  /**
   * Logs an access control (mutation) event. Computes field-level diffs from raw snapshots
   * asynchronously. When halt-on-failure is enabled, blocks and rethrows {@link
   * AuditLogFailureException} on the caller's thread.
   */
  public CompletableFuture<Boolean> logAccessControlEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String resourceId,
      JsonNode input,
      JsonNode output,
      JsonNode signatureNode,
      Map<String, AuditLogContext.EntitySnapshot> snapshots,
      String logUUID) {

    if (!isAuditLoggingEnabled()) return CompletableFuture.completedFuture(true);

    CompletableFuture<Boolean> future =
        CompletableFuture.supplyAsync(
            () ->
                doLogAccessControlEvent(
                    eventScope,
                    eventStatus,
                    resourceType,
                    resourceId,
                    input,
                    output,
                    signatureNode,
                    snapshots,
                    logUUID),
            taskLoggerExecutor);

    awaitIfHaltOnFailure(future);
    return future;
  }

  /** Internal: performs the access control audit log on the executor thread. */
  private boolean doLogAccessControlEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String resourceId,
      JsonNode input,
      JsonNode output,
      JsonNode signatureNode,
      Map<String, AuditLogContext.EntitySnapshot> snapshots,
      String logUUID) {
    boolean status = false;
    try {
      JsonNode entityDiffsNode = ObjectDiffUtils.computeEntityDiffsNode(snapshots, objectMapper);
      status =
          logService.logRequestEvent(
              eventScope,
              eventStatus,
              resourceType,
              resourceId,
              input,
              output,
              signatureNode,
              entityDiffsNode,
              Level.WARNING,
              logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit logging failed: {}", e.getMessage(), e);
    }

    if (!status) {
      log.warn("[AUDIT] Failed to log access control event for {}.{}", resourceType, eventScope);
      prepareLogFailure();
    }
    return status;
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
