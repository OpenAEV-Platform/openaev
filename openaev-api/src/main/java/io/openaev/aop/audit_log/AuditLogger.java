package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import io.openaev.utils.object.ObjectDiffUtils;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that intercepts {@link AccessControl}-annotated controller methods to produce audit
 * log events for CRUD operations.
 *
 * <p>Runs <b>after</b> {@link AccessControlAspect} (which uses {@code @Before}) — the RBAC check
 * has already passed when this aspect's {@code @Around} advice executes.
 *
 * <p>Phase 1: delegates to {@link LogService} for console-only output.
 */
@Component
@ConditionalOnExpression("!'${openaev.audit-logs.transports:}'.isEmpty()")
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

  private final AuditShutdownService auditShutdownService;
  private final AuditLogProperties auditLogProperties;
  private final LogService logService;
  private final ObjectMapper objectMapper;

  public boolean isAuditLoggingEnabled() {
    return logService.isEnabled();
  }

  public boolean isAuditLoggingValid(Action action) {
    return !shouldSkip(action);
  }

  public void prepareLogFailure() {
    if (!auditLogProperties.isHaltOnFailure()) {
      log.info("[AUDIT] Halt-on-failure disabled, application continues running...");
      return;
    }

    log.error("[AUDIT] Halt-on-failure triggered — rolling back and shutting down.");

    // Schedule application shutdown on a separate thread so the current transaction
    // can rollback first (the throw below unwinds the call stack before the shutdown runs).
    auditShutdownService.initiateShutdown();

    throw new AuditLogFailureException(
        "Audit transport failed with halt-on-failure enabled — transaction rolled back.");
  }

  /**
   * Log Authentication events Wraps the audit service call in try/catch — non-blocking by default,
   * but may terminate the process when stop-the-world audit failure handling is enabled.
   */
  @Async("taskLoggerExecutor")
  public CompletableFuture<Boolean> logAuthEventWithRequestContext(
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData rcd,
      String eventScope,
      String eventStatus,
      String provider,
      String reason,
      String logUUID) {

    if (rcd != null) {
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.setRequestContextData(rcd);
    }

    return logAuthEvent(eventScope, eventStatus, provider, reason, logUUID);
  }

  /**
   * Log Authentication events Wraps the audit service call in try/catch — non-blocking by default,
   * but may terminate the process when stop-the-world audit failure handling is enabled.
   */
  @Async("taskLoggerExecutor")
  public CompletableFuture<Boolean> logAuthEvent(
      String eventScope, String eventStatus, String provider, String reason, String logUUID) {
    if (!isAuditLoggingEnabled()) return CompletableFuture.completedFuture(true);

    boolean status = false;

    try {
      status =
          logService.logAuthEvent(
              eventScope, eventStatus, provider, reason, Level.WARNING, logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit auth logging failed (non-blocking): {}", e.getMessage(), e);
    }

    if (!status) {
      log.warn("[AUDIT] Failed to log auth event for {}.{}", provider, eventScope);

      prepareLogFailure();
    }

    return CompletableFuture.completedFuture(status);
  }

  /**
   * Log Mutation events. Computes field-level diffs from raw snapshots asynchronously. Wraps the
   * audit service call in try/catch — non-blocking by default, but may terminate the process when
   * stop-the-world audit failure handling is enabled.
   */
  @Async("taskLoggerExecutor")
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
      log.warn("[AUDIT] Audit logging failed (non-blocking): {}", e.getMessage(), e);
    }

    if (!status) {
      log.warn("[AUDIT] Failed to log access control event for {}.{}", resourceType, eventScope);

      prepareLogFailure();
    }

    return CompletableFuture.completedFuture(status);
  }

  private boolean shouldSkip(Action action) {
    return switch (action) {
      case CREATE, WRITE, DELETE, LAUNCH, DUPLICATE -> false;
      // READ/SEARCH are never audited on success — only unauthorized attempts are logged
      // (captured separately via logAuthEvent when RBAC denies access).
      case READ, SEARCH -> true;
      default -> true; // SKIP_RBAC, PROCESS
    };
  }
}
