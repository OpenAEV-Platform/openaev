package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.LogService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.log.LogUtils;
import java.lang.annotation.Annotation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AOP aspect that intercepts {@link AccessControl}-annotated controller methods to produce audit
 * log events for CRUD operations.
 *
 * <p>Runs <b>after</b> {@link AccessControlAspect} (which uses {@code @Before}) — the RBAC check
 * has already passed when this aspect's {@code @Around} advice executes.
 *
 * <p>Phase 1: delegates to {@link LogService} for console-only output.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class AccessControlAuditLogAspect {

  private final AccessControlAuditLogger accessControlAuditLogger;
  private final PreviewFeatureService previewFeatureService;

  private final ObjectMapper objectMapper;

  @Around("@annotation(accessControl)")
  public Object auditAround(ProceedingJoinPoint joinPoint, AccessControl accessControl)
      throws Throwable {
    Object result = null;

    try {
      Action action = accessControl.actionPerformed();

      if (!accessControlAuditLogger.isAuditLoggingEnabled()
          || !accessControlAuditLogger.isAuditLoggingValid(action)
          || !previewFeatureService.isFeatureEnabled(PreviewFeature.AUDIT_LOG)) {
        return joinPoint.proceed();
      }

      String logUUID = UUID.randomUUID().toString();
      ResourceType resourceType = accessControl.resourceType();

      // Capture the input DTO for create/update/status_change
      String eventScope = LogUtils.getEventScope(action);
      JsonNode inputNode = getInputNode(joinPoint, eventScope);

      // Execute the business operation
      String eventStatus;

      try {
        result = joinPoint.proceed();
        eventStatus = "success";
      } catch (Throwable ex) {
        eventStatus = "error";

        JsonNode resultNode = getOutputNode(result);
        accessControlAuditLogger.logRequestEvent(
            eventScope, eventStatus, resourceType, inputNode, resultNode, logUUID);

        throw ex;
      }

      JsonNode resultNode = getOutputNode(result);
      accessControlAuditLogger.logRequestEvent(
          eventScope, eventStatus, resourceType, inputNode, resultNode, logUUID);
    } catch (Exception e) {
      // Still log the audit event, then continue
      log.warn("[AUDIT] Audit logging failed (non-blocking): {}", e.getMessage(), e);
      // TODO AUDIT: Do we only want to log this to log var or do we want to call the
      // correspondent
      // LogService?
    }

    return result;
  }

  // Capture the input DTO for create/update/status_change
  private JsonNode getInputNode(ProceedingJoinPoint joinPoint, String eventScope) {
    JsonNode inputNode = null;

    if ("create".equals(eventScope)
        || "update".equals(eventScope)
        || "status_change".equals(eventScope)) {
      Object requestBody = findRequestBody(joinPoint);

      if (requestBody != null) {
        inputNode = objectMapper.valueToTree(requestBody);
      }
    }

    return inputNode;
  }

  private JsonNode getOutputNode(Object output) {
    try {
      return output != null ? objectMapper.valueToTree(output) : null;
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to serialize output: {}", e.getMessage(), e);
    }

    return null;
  }

  /** Finds the first method argument annotated with {@code @RequestBody}. */
  private Object findRequestBody(ProceedingJoinPoint joinPoint) {
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
      Object[] args = joinPoint.getArgs();
      for (int i = 0; i < paramAnnotations.length; i++) {
        for (Annotation ann : paramAnnotations[i]) {
          if (ann instanceof RequestBody) {
            return args[i];
          }
        }
      }
    } catch (Exception e) {
      log.debug("[AUDIT] Failed to find @RequestBody argument: {}", e.getMessage());
    }
    return null;
  }
}
