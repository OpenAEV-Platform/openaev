package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

  @Value("${openaev.audit-logs.stop-the-world:false}")
  private boolean stw;

  private final ApplicationContext context;
  private final AuditRequestValidator auditRequestValidator;
  private final LogService logService;

  public boolean isAuditLoggingEnabled() {
    return logService.isEnabled();
  }

  public boolean isAuditLoggingValid(Action action) {
    return auditRequestValidator.valid(action);
  }

  public boolean isAuditUnauthorizedLoggingValid() {
    return auditRequestValidator.validUnauthorized();
  }

  public void prepareLogFailure() {
    // Halt the application when stop-the-world is enabled and a log failure occurs.
    if (stw) {
      log.error("[AUDIT] Stop-the-world triggered — shutting down application.");

      try {
        int exitCode = SpringApplication.exit(context, () -> 1);
        System.exit(exitCode);
      } catch (Exception e) {
        log.warn("[AUDIT] Failed to stop application after log failure: {}", e.getMessage(), e);
      }
    } else {
      log.warn("[AUDIT] Stop-the-world disabled, application continue running...");
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
      prepareLogFailure();
    }

    return CompletableFuture.completedFuture(status);
  }

  /**
   * Log Mutation events Wraps the audit service call in try/catch — non-blocking by default, but
   * may terminate the process when stop-the-world audit failure handling is enabled.
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
      String logUUID) {
    if (!isAuditLoggingEnabled()) return CompletableFuture.completedFuture(true);

    boolean status = false;

    try {
      status =
          logService.logRequestEvent(
              eventScope,
              eventStatus,
              resourceType,
              resourceId,
              input,
              output,
              signatureNode,
              Level.WARNING,
              logUUID);

    } catch (Exception e) {
      log.warn("[AUDIT] Audit logging failed (non-blocking): {}", e.getMessage(), e);
    }

    if (!status) {
      prepareLogFailure();
    }

    return CompletableFuture.completedFuture(status);
  }
}
