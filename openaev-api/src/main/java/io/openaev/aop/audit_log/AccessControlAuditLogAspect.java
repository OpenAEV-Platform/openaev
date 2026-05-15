package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.database.model.Action;
import io.openaev.database.model.Base;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import io.openaev.utils.ResourceManagerUtils;
import io.openaev.utils.log.LogUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static java.util.logging.Level.INFO;

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


    private final AuditRequestValidator auditRequestValidator;
    private final AuditResourceDetector auditResourceDetector;
    private final AccessControlAuditLogger accessControlAuditLogger;

    private final ObjectMapper objectMapper;

    @Around("@annotation(accessControl)")
    public Object auditAround(ProceedingJoinPoint joinPoint, AccessControl accessControl)
            throws Throwable {
        Action action = accessControl.actionPerformed();

        if (!auditRequestValidator.valid(action)) {
            return joinPoint.proceed();
        }

        String eventScope = LogUtils.getEventScope(action);

        // -- Pre-execution:get child-resource --
        AuditResourceDetector.AuditResourceInfo resourceInfo = auditResourceDetector.detectResourceBeforeExecution(joinPoint, accessControl, eventScope);
        ResourceType resourceType = resourceInfo.resourceType();
        String resourceId = resourceInfo.resourceId();
        String sourceId = resourceInfo.sourceId();
        ResourceType entityType = resourceInfo.entityType();
        String entityId = resourceInfo.entityId();
        String entityName = resourceInfo.entityName();
        JsonNode entitySnapshot = resourceInfo.entitySnapshot();

        // Capture the input DTO for create/update/status_change
        JsonNode inputNode = getInputNode(joinPoint, eventScope);

        // Execute the business operation
        Object result;
        String eventStatus;

        try {
            result = joinPoint.proceed();
            eventStatus = "success";
        } catch (Throwable ex) {
            eventStatus = "error";

            accessControlAuditLogger.prepareLogFailure();

            // Still log the audit event, then re-throw
            accessControlAuditLogger.logMutationEvent(
                    eventScope,
                    eventStatus,
                    resourceType,
                    resourceId,
                    inputNode,
                    null,
                    entityName,
                    null,
                    sourceId);

            throw ex;
        }

        CompletableFuture<Boolean> future = accessControlAuditLogger.auditEvent(
                eventScope,
                eventStatus,
                resourceType,
                resourceId,
                sourceId,
                entityType,
                entityId,
                entityName,
                entitySnapshot,
                inputNode,
                result
        );
        future.join();//TODO: to be deleted. Only for testing until I have the HttpReqRespUtils.getCurrentRequest(); exception fixed.

        return result;
    }

    // Capture the input DTO for create/update/status_change
    private JsonNode getInputNode(ProceedingJoinPoint joinPoint, String eventScope) {
        JsonNode inputNode = null;

        if ("create".equals(eventScope) || "update".equals(eventScope) || "status_change".equals(eventScope)) {
            Object requestBody = findRequestBody(joinPoint);

            if (requestBody != null) {
                inputNode = objectMapper.valueToTree(requestBody);
            }
        }

        return inputNode;
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
