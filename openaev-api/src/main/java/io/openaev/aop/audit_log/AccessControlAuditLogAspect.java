package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.ResourceType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * AOP aspect that intercepts {@link AccessControl}-annotated controller methods to produce audit
 * log events for CRUD operations.
 *
 * <p>This {@code @Around} aspect <b>wraps</b> the {@link AccessControlAspect} ({@code @Before},
 * {@code @Order(LOWEST_PRECEDENCE)}). The RBAC check runs <b>during</b> {@code proceed()} — if it
 * denies access, the thrown {@code ResponseStatusException(403)} is caught here and logged as an
 * "unauthorized" audit event before being re-thrown.
 *
 * <p>The aspect order is {@code LOWEST_PRECEDENCE - 1} so it runs <b>inside</b> the transaction
 * boundary ({@code LOWEST_PRECEDENCE - 4}) and the tenant scoping aspects ({@code LOWEST_PRECEDENCE
 * - 3} / {@code - 2}), and <b>outside</b> the RBAC aspect ({@code LOWEST_PRECEDENCE}). When
 * halt-on-failure is active and the audit transport fails, the thrown {@link
 * AuditLogFailureException} propagates through the transaction interceptor, which rolls back the
 * mutation.
 *
 * <p>Phase 1: delegates to {@link io.openaev.service.LogService} for console-only output.
 */
@Aspect
@Component
@ConditionalOnExpression("!'${openaev.audit-logs.transports:}'.isEmpty()")
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
@Slf4j
public class AccessControlAuditLogAspect {

  private final AuditLogger auditLogger;

  private final ObjectMapper objectMapper;
  private final ExpressionParser parser = new SpelExpressionParser();
  @PersistenceContext private EntityManager entityManager;

  private static final String LOG_ERROR_MSG = "Error during audit logging";

  @Around("@annotation(io.openaev.aop.AccessControl)")
  public Object auditAround(ProceedingJoinPoint joinPoint) throws Throwable {
    AccessControl accessControl = resolveAccessControlAnnotation(joinPoint);

    // TODO: Logs should be based in HTTP interceptor and not only in the access control annotation.

    if (accessControl == null) return joinPoint.proceed();

    Action action = null;
    boolean isActive = false;
    boolean isActionActive = false;

    try {
      action = accessControl.actionPerformed();
      isActive = auditLogger.isAuditLoggingEnabled();
      isActionActive = auditLogger.isAuditLoggingValid(action);
    } catch (Exception e) {
      log.warn(LOG_ERROR_MSG, e);
    }

    Object result = null;

    try {
      result = joinPoint.proceed();
    } catch (Throwable ex) {
      if (isActive) {
        try {
          if (isRbacDeniedException(ex)) {
            JsonNode errorNode = buildErrorNode(null, ex);

            logAccessControlEvent(
                joinPoint,
                accessControl,
                AuditEventScope.from(Action.UNAUTHORIZED),
                EventStatus.ERROR,
                errorNode);
          } else if (isActionActive) {
            JsonNode resultNode = getOutputNode(result);
            JsonNode errorNode = buildErrorNode(resultNode, ex);

            logAccessControlEvent(
                joinPoint,
                accessControl,
                AuditEventScope.from(action),
                EventStatus.ERROR,
                errorNode);
          }
        } catch (AuditLogFailureException e) {
          throw e;
        } catch (Exception e) {
          log.warn(LOG_ERROR_MSG, e);
        }
      }
      throw ex;
    }

    if (isActive && isActionActive && AuditLogContext.isEnabled()) {
      try {
        JsonNode resultNode = getOutputNode(result);

        logAccessControlEvent(
            joinPoint,
            accessControl,
            AuditEventScope.from(action),
            EventStatus.SUCCESS,
            resultNode);
      } catch (AuditLogFailureException ex) {
        throw ex;
      } catch (Exception ex) {
        log.warn(LOG_ERROR_MSG, ex);
      }
    }

    return result;
  }

  private void logAccessControlEvent(
      JoinPoint joinPoint,
      AccessControl accessControl,
      AuditEventScope eventScope,
      EventStatus eventStatus,
      JsonNode outputNode) {
    try {
      ResourceType resourceType = accessControl.resourceType();
      String resourceId = resolveResourceId(joinPoint, accessControl);
      int payloadIndex = findRequestPayloadIndex(joinPoint);
      JsonNode inputNode = getInputNode(joinPoint, payloadIndex, eventScope);
      JsonNode signatureNode = getMethodSignature(joinPoint, payloadIndex);

      // Capture snapshots on the servlet thread before any async handoff.
      Map<String, AuditLogContext.EntitySnapshot> snapshots = captureEntitySnapshots();

      BiConsumer<Boolean, Throwable> logCompletion =
          (success, throwable) -> {
            if (throwable != null || (success != null && !success)) {
              log.warn(
                  "[AUDIT] Failed to log access control event for {}.{}", resourceType, eventScope);
              if (throwable != null) {
                log.warn(LOG_ERROR_MSG, throwable);
              }
            }
          };

      CompletableFuture<Boolean> auditFuture =
          auditLogger.logAccessControlEvent(
              eventScope,
              eventStatus,
              resourceType,
              resourceId,
              inputNode,
              outputNode,
              signatureNode,
              snapshots);

      auditFuture.whenComplete(logCompletion);
    } catch (AuditLogFailureException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn(LOG_ERROR_MSG, ex);
    }
  }

  /**
   * Flushes pending Hibernate changes and captures all entity snapshots from {@link
   * AuditLogContext}. Must be called on the servlet thread before any async handoff, since {@link
   * AuditLogContext} is request-scoped.
   *
   * <p>The flush triggers {@code @PreUpdate} / {@code @PreRemove} JPA callbacks that store
   * before/after snapshots in {@link AuditLogContext}. Without it, the callbacks would only fire at
   * transaction commit time — after {@code consumeAllSnapshots()} has already cleared the context.
   *
   * <p>This is safe because the aspect runs inside the transaction boundary; if the flush reveals a
   * constraint violation, it would have failed at commit anyway.
   */
  private Map<String, AuditLogContext.EntitySnapshot> captureEntitySnapshots() {
    try {
      entityManager.flush();
      return AuditLogContext.consumeAllSnapshots();
    } catch (Exception e) {
      log.debug("[AUDIT] Failed to capture entity snapshots: {}", e.getMessage());
      AuditLogContext.clear();
      return null;
    }
  }

  private AccessControl resolveAccessControlAnnotation(JoinPoint joinPoint) {
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      AccessControl annotation = signature.getMethod().getAnnotation(AccessControl.class);
      if (annotation != null) {
        return annotation;
      }

      return joinPoint
          .getTarget()
          .getClass()
          .getMethod(signature.getName(), signature.getMethod().getParameterTypes())
          .getAnnotation(AccessControl.class);
    } catch (Exception ex) {
      log.warn(LOG_ERROR_MSG, ex);
      return null;
    }
  }

  // Capture the input DTO for create/update/status_change
  private JsonNode getInputNode(JoinPoint joinPoint, int payloadIndex, AuditEventScope eventScope) {
    if (payloadIndex < 0
        || (eventScope != AuditEventScope.CREATE
            && eventScope != AuditEventScope.UPDATE
            && eventScope != AuditEventScope.STATUS_CHANGE)) {
      return null;
    }

    Object requestBody = joinPoint.getArgs()[payloadIndex];
    return requestBody != null ? objectMapper.valueToTree(requestBody) : null;
  }

  private JsonNode getOutputNode(Object output) {
    try {
      return output != null ? objectMapper.valueToTree(output) : null;
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to serialize output: {}", e.getMessage(), e);
    }

    return null;
  }

  /**
   * Wraps the (optional) partial result and the thrown exception into a single {@link JsonNode} so
   * the error context is captured in one audit field.
   */
  private JsonNode buildErrorNode(JsonNode resultNode, Throwable ex) {
    com.fasterxml.jackson.databind.node.ObjectNode errorNode = objectMapper.createObjectNode();
    if (resultNode != null) {
      errorNode.set("result", resultNode);
    }
    errorNode.put("exception_type", ex.getClass().getName());
    if (ex.getMessage() != null) {
      errorNode.put("exception_message", ex.getMessage());
    }
    return errorNode;
  }

  /**
   * Finds the index of the argument carrying the request payload.
   *
   * <p>Recognizes {@code @RequestBody}, {@code @RequestPart} and {@code @ModelAttribute}. Multipart
   * endpoints (e.g. {@code InjectorApi.registerInjector}) pass their DTO via {@code @RequestPart};
   * missing them made the payload fall through to the {@code signature} node, which at the time was
   * not size-capped by {@code ObjectNormalizationUtils} — a heap-exhaustion risk on large payloads.
   * {@code LogService} now normalizes the signature node as well, as defense in depth.
   *
   * @return the argument index, or {@code -1} if none matches
   */
  private int findRequestPayloadIndex(JoinPoint joinPoint) {
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
      Object[] args = joinPoint.getArgs();
      for (int i = 0; i < paramAnnotations.length && i < args.length; i++) {
        for (Annotation ann : paramAnnotations[i]) {
          boolean isPayloadAnnotation =
              ann instanceof RequestBody
                  || ann instanceof RequestPart
                  || ann instanceof ModelAttribute;
          if (isPayloadAnnotation && !isNonSerializableArgument(args[i])) {
            return i;
          }
        }
      }
    } catch (Exception e) {
      log.debug("[AUDIT] Failed to find request payload argument: {}", e.getMessage());
    }
    return -1;
  }

  /**
   * Arguments that must never be fed to Jackson: serializing them either materializes the whole
   * uploaded file in the heap ({@code MultipartFile#getBytes} as base64) or consumes a stream.
   */
  private static boolean isNonSerializableArgument(Object value) {
    Object unwrapped = value instanceof Optional<?> opt ? opt.orElse(null) : value;
    return unwrapped instanceof MultipartFile
        || unwrapped instanceof Resource
        || unwrapped instanceof InputStream
        || unwrapped instanceof HttpServletRequest
        || unwrapped instanceof HttpServletResponse;
  }

  /** Builds a JsonNode with the method signature and its parameter names mapped to their values. */
  private JsonNode getMethodSignature(JoinPoint joinPoint, int payloadIndex) {
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      String[] paramNames = signature.getParameterNames();
      Object[] args = joinPoint.getArgs();

      ObjectNode node = objectMapper.createObjectNode();
      node.put("method", signature.getDeclaringTypeName() + "." + signature.getName());

      ObjectNode params = objectMapper.createObjectNode();
      if (paramNames != null) {
        for (int i = 0; i < paramNames.length; i++) {
          // Never re-serialize the payload into the signature; for mutating scopes it is captured
          // separately as inputNode (normalized and size-capped downstream).
          if (i == payloadIndex) {
            params.put(paramNames[i], "@RequestBody");
          } else {
            setParameterNode(params, paramNames[i], i < args.length ? args[i] : null);
          }
        }
      }
      node.set("parameters", params);
      return node;
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to build method signature node: {}", e.getMessage(), e);
    }
    return null;
  }

  /** Serializes a single method parameter, skipping values Jackson must never walk. */
  private void setParameterNode(ObjectNode params, String paramName, Object value) {
    Object unwrapped = value instanceof Optional<?> opt ? opt.orElse(null) : value;
    if (unwrapped != null && isNonSerializableArgument(unwrapped)) {
      params.put(paramName, unwrapped.getClass().getSimpleName());
      return;
    }
    try {
      params.set(paramName, objectMapper.valueToTree(value));
    } catch (Exception ex) {
      params.put(paramName, value != null ? value.toString() : "null");
    }
  }

  /** Resolves the resource ID from the annotation's SpEL expression. */
  public String resolveResourceId(JoinPoint joinPoint, AccessControl accessControl) {
    if (accessControl.resourceId().isEmpty()) {
      return "";
    }
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      String[] paramNames = signature.getParameterNames();
      Object[] args = joinPoint.getArgs();

      EvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
      if (paramNames != null) {
        for (int i = 0; i < paramNames.length; i++) {
          ctx.setVariable(paramNames[i], args[i]);
        }
      }

      Object value = parser.parseExpression(accessControl.resourceId()).getValue(ctx);
      return value != null ? value.toString() : "";
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to resolve resourceId SpEL: {}", e.getMessage(), e);
      return "";
    }
  }

  private boolean isRbacDeniedException(Throwable exception) {
    try {
      return exception instanceof ResponseStatusException rse
          && HttpStatus.FORBIDDEN.equals(rse.getStatusCode());
    } catch (Exception e) {
      // return false
    }

    return false;
  }
}
