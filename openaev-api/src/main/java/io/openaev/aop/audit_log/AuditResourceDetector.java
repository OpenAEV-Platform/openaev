package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.database.model.ResourceType;
import io.openaev.utils.ResourceManagerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditResourceDetector {

    /** Info about a child resource detected before deletion. */
    public record ChildResourceInfo(ResourceType childType, String childId, JsonNode childSnapshot) {};
    public record AuditResourceInfo(ResourceType resourceType, String resourceId, String sourceId, ResourceType entityType, String entityId, String entityName, JsonNode entitySnapshot) {};

    private final ExpressionParser parser = new SpelExpressionParser();

    private final ResourceManagerUtils resourceManagerUtils;

    public AuditResourceInfo detectResourceBeforeExecution(ProceedingJoinPoint joinPoint, AccessControl accessControl, String eventScope) throws Throwable {
        ResourceType resourceType = accessControl.resourceType();
        String resourceId = resolveResourceId(joinPoint, accessControl);

        // -- Pre-execution: child-resource detection --
        // If Action.WRITE on a parent, scan for child entity IDs in other @PathVariable params.
        // This covers child create (no child found), update (child found), and delete (HTTP DELETE).
        // Must happen BEFORE proceed() so we can snapshot the child before it's modified/deleted.
        ChildResourceInfo childInfo = null;
        if ("update".equals(eventScope)) {
            childInfo = detectChildResource(joinPoint, resourceType, resourceId);
        }

        // For updates/deletes/status_change: pre-fetch entity state before the mutation
        // For duplicates: snapshot source entity to capture its name for the audit message
        String sourceId = null;
        ResourceType entityType = null;
        String entityId = null;
        String entityName = null;
        JsonNode entitySnapshot = null;

        if (childInfo != null) {
            // Child operation: snapshot the child, not the parent
            entitySnapshot = childInfo.childSnapshot();
            entityId = childInfo.childId();
            entityType = childInfo.childType();
            entityName = ResourceManagerUtils.extractNameFromSnapshot(entitySnapshot);
        } else if (!resourceId.isEmpty()) {
            if ("update".equals(eventScope) || "delete".equals(eventScope) || "status_change".equals(eventScope)) {
                entitySnapshot = resourceManagerUtils.snapshotResourceEntity(resourceType, resourceId);
                entityName = ResourceManagerUtils.extractNameFromSnapshot(entitySnapshot);
            } else if ("duplicate".equals(eventScope)) {
                // For duplicates: remember the source entity ID and resolve its name
                sourceId = resourceId;
                JsonNode sourceSnapshot = resourceManagerUtils.snapshotResourceEntity(resourceType, resourceId);
                entityName = ResourceManagerUtils.extractNameFromSnapshot(sourceSnapshot);
            }
        }

        return new AuditResourceInfo(
                resourceType,
                resourceId,
                sourceId,
                entityType,
                entityId,
                entityName,
                entitySnapshot
        );
    }

    /** Resolves the resource ID from the annotation's SpEL expression. */
    public String resolveResourceId(ProceedingJoinPoint joinPoint, AccessControl accessControl) {
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
            log.debug("[AUDIT] Failed to resolve resourceId SpEL: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Detects a child-resource by scanning {@code @PathVariable} parameters for IDs that are not the
     * parent's resourceId. For each candidate, tries {@code EntityManager.find()} against the entity
     * class map until a match is found. Returns the child info with a pre-deletion snapshot, or null.
     *
     * TODO: optimize this logic so it can cache foir the next requests.
     */
    public ChildResourceInfo detectChildResource(ProceedingJoinPoint joinPoint, ResourceType resourceType, String parentResourceId) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            Annotation[][] paramAnnotations = sig.getMethod().getParameterAnnotations();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < paramAnnotations.length; i++) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable) {
                        String paramValue = args[i] != null ? args[i].toString() : null;

                        if (paramValue != null && !paramValue.equals(parentResourceId)) {
                            JsonNode snapshot = resourceManagerUtils.snapshotResourceEntity(resourceType, paramValue);

                            if (snapshot != null) {
                                return new ChildResourceInfo(resourceType, paramValue, snapshot);
                            }

                            // Non-parent path variable — try to find the entity
                            ChildResourceInfo childInfo = null;

                            for (Map.Entry<ResourceType, Class<?>> entry : ResourceManagerUtils.ENTITY_CLASS_MAP.entrySet()) {
                                childInfo = getChildResourceInfo(entry.getKey(), paramValue, false);

                                if (childInfo != null)
                                    return childInfo;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[AUDIT] Failed to detect child resource: {}", e.getMessage());
        }
        return null;
    }

    private ChildResourceInfo getChildResourceInfo(ResourceType resourceType, String resourceId, boolean logException) {
        try {
            JsonNode snapshot = resourceManagerUtils.snapshotResourceEntity(resourceType, resourceId);

            if (snapshot != null) {
                return new ChildResourceInfo(resourceType, resourceId, snapshot);
            }
        } catch (Exception e) {
            if (logException)
                log.debug("[AUDIT] Failed to get child resource info for {}/{}: {}", resourceType, resourceId, e.getMessage());
        }
        return null;
    }
}
