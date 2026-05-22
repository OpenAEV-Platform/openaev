package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@RequiredArgsConstructor
@Slf4j
public class AccessControlAuditLogger {

  @Value("${openaev.audit-logs.stop-the-world:false}")
  private boolean stw;

  private final AuditRequestValidator auditRequestValidator;

  private final LogService logService;

  public boolean isAuditLoggingEnabled() {
    return logService.isEnabled(LogService.AuditLogType.AUDIT);
  }

  public boolean isAuditLoggingValid(Action action) {
    return auditRequestValidator.valid(action);
  }

  /** Wraps the audit service call in try/catch — audit must never break the business flow. */
  @Async("accessControlAuditLoggerExecutor")
  public CompletableFuture<Boolean> logAuthEvent(
      String eventScope, String eventStatus, String provider, String reason, String logUUID) {
    boolean status = false;

    try {
      status =
          logService.logAuthEvent(
              eventScope,
              eventStatus,
              provider,
              reason,
              Level.WARNING,
              LogService.AuditLogType.AUDIT,
              logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit auth logging failed (non-blocking): {}", e.getMessage(), e);
    }

    return CompletableFuture.completedFuture(status);
  }

  @Async("accessControlAuditLoggerExecutor")
  public CompletableFuture<Boolean> logAccessControlEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String resourceId,
      JsonNode input,
      JsonNode output,
      JsonNode signatureNode,
      String logUUID) {
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
              LogService.AuditLogType.AUDIT,
              logUUID);

    } catch (Exception e) {
      log.warn("[AUDIT] Generic logging failed (non-blocking): {}", e.getMessage(), e);
    }

    return CompletableFuture.completedFuture(status);
  }
}
