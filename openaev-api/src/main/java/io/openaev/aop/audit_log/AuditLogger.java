package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.database.audit.EntityDiffContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import io.openaev.utils.ObjectDiffUtils;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
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

  private final ApplicationContext context;
  private final AuditLogProperties auditLogProperties;
  private final LogService logService;
  private final ObjectMapper objectMapper;
  private final AtomicBoolean shutdownTriggered = new AtomicBoolean(false);

  public boolean isAuditLoggingEnabled() {
    return logService.isEnabled();
  }

  public boolean isAuditLoggingValid(Action action) {
    return !shouldSkip(action);
  }

  public void prepareLogFailure() {
    // Halt the application when Halt-on-failure is enabled and a log failure occurs.
    try {
      if (!auditLogProperties.isHaltOnFailure()) {
        log.info("[AUDIT] Halt-on-failure disabled, application continue running...");
        return;
      }

      if (!shutdownTriggered.compareAndSet(false, true)) {
        log.debug("[AUDIT] Shutdown already triggered by another thread.");
        return;
      }

      log.error("[AUDIT] Halt-on-failure triggered - shutting down application.");
      int exitCode = SpringApplication.exit(context);
      if (exitCode == 0) {
        exitCode = 1; // optional fallback policy
      }

      System.exit(exitCode);
      log.error("[AUDIT] Spring shutdown initiated with exit code {}.", exitCode);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to execute log failure action: {}", e.getMessage(), e);
    }
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
      Map<String, EntityDiffContext.EntitySnapshot> snapshots,
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
