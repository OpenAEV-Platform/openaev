package io.openaev.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.engine.EngineService;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.dispatcher.LogTransportDispatcherUtils;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.object.ObjectDiffUtils;
import io.openaev.utils.object.ObjectRedactionUtils;
import io.openaev.utils.log.LogUtils;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.object.ObjectNormalizationUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Log service — builds structured {@link LogEvent} events for CRUD and authentication
 * operations.
 */
@Service
@Slf4j
public class LogService {

    @Value("${openaev.logs.enabled:false}")
    private boolean logsEnabled;

    @Value("${openaev.audit-logs.enabled:false}")
    private boolean auditLogsEnabled;

    public static enum AuditLogType {
        DEFAULT,
        AUDIT
    };

    private final LogTransportDispatcherUtils logTransportDispatcherUtils;

    private final AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

    private final ObjectNormalizationUtils objectNormalizationUtils;
    private final ObjectDiffUtils objectDiffUtils;

    /**
     * ObjectMapper reused from the search engine driver — guarantees identical serialization between
     * the log appender and the ES/OS transport. Resolved lazily from {@link EngineService} because
     * the engine bean may not be available at construction time.
     */
    private final ObjectMapper objectMapper;

    private final UserService userService;

    public LogService(
            UserService userService,
            EngineService engineService,
            LogTransportDispatcherUtils logTransportDispatcherUtils,
            AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils,
            ObjectNormalizationUtils objectNormalizationUtils,
            ObjectDiffUtils objectDiffUtils
    ) {
        this.userService = userService;
        this.objectMapper = engineService.getObjectMapper();
        this.logTransportDispatcherUtils = logTransportDispatcherUtils;
        this.auditLogTransportDispatcherUtils = auditLogTransportDispatcherUtils;
        this.objectNormalizationUtils = objectNormalizationUtils;
        this.objectDiffUtils = objectDiffUtils;
    }

    // -- Public API --

    /**
     * Logs a mutation (create/update/delete/duplicate/status_change) audit event but only if the snapshots are different.
     *
     * @param eventScope "create", "update", "delete", "duplicate", or "status_change"
     * @param eventStatus "success" or "error"
     * @param resourceType the resource type from the {@code @AccessControl} annotation
     * @param entityId the resolved entity ID (may be empty for creates before persist)
     * @param newSnapshot the serialized input DTO (for create/update); null for delete/duplicate
     * @param oldSnapshot the serialized previous values (for update); null for create/delete/duplicate
     * @param entityName human-readable entity name for the message (e.g. scenario name)
     * @param parentId the parent entity ID when a child is created within a parent; null otherwise
     * @param sourceId the source entity ID for duplicate events; null for other event types
     */
    public boolean logMutationEventIfDifferentSnapshots(
            String eventScope,
            String eventStatus,
            ResourceType resourceType,
            String entityId,
            JsonNode newSnapshot,
            JsonNode oldSnapshot,
            String entityName,
            String parentId,
            String sourceId,
            Object logLevel,
            AuditLogType logType
    ) {
        if (!isEnabled(logType)) {
            return true;
        }

        // For updates: compute diff between old and new values
        ObjectDiffUtils.DiffResult diffResult = diffObjects(eventScope, oldSnapshot, newSnapshot);

        // Only log if there are differences, this is, if no meaningful changes detected — skip the audit event (no-op update)
        if (diffResult != null) {
            JsonNode diffNewValues = diffResult.newValues();
            JsonNode diffOldValues = diffResult.oldValues();

            return logMutationEvent(
                    eventScope,
                    eventStatus,
                    resourceType,
                    entityId,
                    diffNewValues,
                    diffOldValues,
                    entityName,
                    parentId,
                    sourceId,
                    logLevel,
                    logType
            );
        }

        return true;
    }

    /**
     * Logs a mutation (create/update/delete/duplicate/status_change) audit event.
     *
     * @param eventScope "create", "update", "delete", "duplicate", or "status_change"
     * @param eventStatus "success" or "error"
     * @param resourceType the resource type from the {@code @AccessControl} annotation
     * @param entityId the resolved entity ID (may be empty for creates before persist)
     * @param input the serialized input DTO (for create/update); null for delete/duplicate
     * @param oldValue the serialized previous values (for update); null for create/delete/duplicate
     * @param entityName human-readable entity name for the message (e.g. scenario name)
     * @param parentId the parent entity ID when a child is created within a parent; null otherwise
     * @param sourceId the source entity ID for duplicate events; null for other event types
     */
    public boolean logMutationEvent(
            String eventScope,
            String eventStatus,
            ResourceType resourceType,
            String entityId,
            JsonNode input,
            JsonNode oldValue,
            String entityName,
            String parentId,
            String sourceId,
            Object logLevel,
            AuditLogType logType
    ) {
        if (!isEnabled(logType)) {
            return true;
        }

        try {
            String entityTypeName = formatResourceType(resourceType);
            String event_access = LogUtils.getEventAccess(resourceType);

            String displayName = entityName != null ? entityName : entityId;
            String message;

            if ("status_change".equals(eventScope)) {
                message = LogUtils.buildStatusChangeMessage(input, entityTypeName, displayName);
            } else {
                message = eventScope + "s " + entityTypeName + " `" + displayName + "`";
            }

            LogEvent doc = buildBaseAuditLog("mutation", eventStatus, event_access, eventScope);

            // -- context_data (LinkedHashMap preserves insertion order) --
            Map<String, Object> ctx = new LinkedHashMap<>();
            if (entityId != null && !entityId.isEmpty()) {
                ctx.put("id", entityId);
            }
            ctx.put("entity_type", entityTypeName);
            if (parentId != null && !parentId.isEmpty()) {
                ctx.put("parent_id", parentId);
            }
            if (sourceId != null && !sourceId.isEmpty()) {
                ctx.put("source_entity_id", sourceId);
            }

            // Redacted input + old_value
            if (input != null) {
                input = objectNormalizationUtils.normalize(input);
                input = ObjectRedactionUtils.redact(input, entityTypeName);
                ctx.put("input", objectMapper.convertValue(input, Map.class));
            }

            if (oldValue != null) {
                oldValue = objectNormalizationUtils.normalize(oldValue);
                oldValue = ObjectRedactionUtils.redact(oldValue, entityTypeName);
                ctx.put("old_value", objectMapper.convertValue(oldValue, Map.class));
            }

            ctx.put("message", message);
            doc.setContextData(ctx);

            return emit(doc, logLevel, logType);
        } catch (Exception e) {
            log.warn("[AUDIT] Failed to log mutation event: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * Logs an authentication audit event.
     *
     * @param eventScope "login", "logout", or "unauthorized"
     * @param eventStatus "success" or "error"
     * @param provider auth provider name (e.g. "local", "auth0", "saml2")
     * @param reason error reason (exception class name); null on success
     */
    public boolean logAuthEvent(
            String eventScope,
            String eventStatus,
            String provider,
            String reason,
            Object logLevel,
            AuditLogType logType
    ) {
        if (!isEnabled(logType)) {
            return true;
        }

        try {
            // Build human-readable message
            String message = LogUtils.buildAuthLogMessage(eventScope, eventStatus, provider);
            String event_access = LogUtils.getAuthEventAccess();
            LogEvent doc = buildBaseAuditLog("authentication", eventStatus, event_access, eventScope);

            // -- context_data --
            Map<String, Object> ctx = new LinkedHashMap<>();
            if (provider != null) {
                ctx.put("provider", provider);
            }
            if (reason != null) {
                ctx.put("reason", reason);
            }
            ctx.put("message", message);
            doc.setContextData(ctx);

            return emit(doc, logLevel, logType);
        } catch (Exception e) {
            log.warn("[AUDIT] Failed to log auth event: {}", e.getMessage(), e);
        }

        return false;
    }

    public boolean logEvent(LogEvent doc, Object logLevel, AuditLogType logType) {
        try {
            if (!isEnabled(logType)) {
                return true;
            }

            return emit(doc, logLevel, logType);
        } catch (Exception e) {
            log.warn("[AUDIT] Failed to log event: {}", e.getMessage(), e);
        }

        return false;
    }

    public boolean logMessage(String message, Object logLevel, AuditLogType logType) {
        try {
            if (!isEnabled(logType)) {
                return true;
            }

            return emit(message, logLevel, logType);
        } catch (Exception e) {
            log.warn("[AUDIT] Failed to log message: {}", e.getMessage(), e);
        }

        return false;
    }

    // -- Internal helpers --

    private boolean isEnabled(AuditLogType logType) {
        return logType == AuditLogType.AUDIT ? auditLogsEnabled : logsEnabled || auditLogsEnabled;
    }

    private ObjectDiffUtils.DiffResult diffObjects(String eventScope, JsonNode entitySnapshot, JsonNode inputNode) {
        // For updates: compute diff between old and new values
        JsonNode diffNewValues = null;
        JsonNode diffOldValues = null;

        if ("update".equals(eventScope)) {
            if (entitySnapshot != null && inputNode != null) {
                ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(entitySnapshot, inputNode);
                diffNewValues = diff.newValues();
                diffOldValues = diff.oldValues();

                if (diffNewValues == null || diffNewValues.isEmpty()) {
                    // No meaningful changes detected — skip the audit event (no-op update)
                    return null;
                }
            } else if (inputNode != null) {
                // No old snapshot available — log input as-is (without diff computation)
                diffNewValues = objectNormalizationUtils.normalize(inputNode);
            }
        } else if ("status_change".equals(eventScope)) {
            // For status changes with a request body (exercise status, scenario recurrence):
            // reuse the diff engine to extract only the changed fields and their old values.
            // For instant launch (no request body): synthesize a minimal input, no old_value.
            if (entitySnapshot != null && inputNode != null) {
                ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(entitySnapshot, inputNode);
                diffNewValues = diff.newValues();
                diffOldValues = diff.oldValues();

                if (diffNewValues == null) {
                    diffNewValues = inputNode;
                }
            } else if (inputNode != null) {
                diffNewValues = inputNode;
            } else {
                // No request body (e.g. POST /scenarios/{id}/exercise/running)
                ObjectNode syntheticInput = objectMapper.createObjectNode();
                syntheticInput.put("action", "launch");
                diffNewValues = syntheticInput;
            }
        } else if ("create".equals(eventScope) && inputNode != null) {
            diffNewValues = objectNormalizationUtils.normalize(inputNode);
        }

        return new ObjectDiffUtils.DiffResult(diffNewValues, diffOldValues);
    }

    /** Builds the common part of an {@link LogEvent} with all envelope and user fields populated. */
    private LogEvent buildBaseAuditLog(
            String eventType, String eventStatus, String eventAccess, String eventScope) {
        Instant now = Instant.now();

        LogEvent doc = new LogEvent();
        doc.setId(UUID.randomUUID().toString());
        doc.setCreatedAt(now);
        doc.setTimestamp(now);

        doc.setEventType(eventType);
        doc.setEventStatus(eventStatus);
        doc.setEventAccess(eventAccess);
        doc.setEventScope(eventScope);

        // User context
        doc.setUserId(resolveUserId());
        populateUserMetadata(doc);

        return doc;
    }

    /** Resolves the current user ID from the security context, or null if anonymous. */
    private String resolveUserId() {
        try {
            OpenAEVPrincipal principal = SessionHelper.currentUser();
            if (principal == null || "anonymous".equals(principal.getId())) {
                return null;
            }
            return principal.getId();
        } catch (Exception e) {
            return null;
        }
    }

    /** Populates user metadata (email, IP, user agent) on the given audit log document. */
    private void populateUserMetadata(LogEvent doc) {
        LogEvent.UserMetadata meta = new LogEvent.UserMetadata();
        boolean hasData = false;

        // User email — denormalized for display
        try {
            String userId = doc.getUserId();
            if (userId != null) {
                User user = userService.user(userId);
                if (user != null && user.getEmail() != null) {
                    meta.setUserEmail(user.getEmail());
                    hasData = true;
                }
            }
        } catch (Exception e) {
            // User not found or not in a request context — skip email
        }

        // HTTP request headers
        HttpServletRequest request = HttpReqRespUtils.getCurrentRequest();
        Map<String, String> headers = HttpReqRespUtils.extractHeaders(request);

        if (headers != null) {
            String userAgent = HttpReqRespUtils.extractHeader(headers, "User-Agent");
            if (userAgent != null) {
                meta.setUserAgent(userAgent);
                hasData = true;
            }

            String xff = HttpReqRespUtils.extractHeader(headers, "X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                meta.setXForwardedFor(xff);
            }

            String ip = HttpReqRespUtils.getClientIpAddressIfServletRequestExist();
            if (ip != null) {
                meta.setIp(ip);
                hasData = true;
            }
        }

        if (hasData) {
            doc.setUserMetadata(meta);
        }
    }

    /** Converts a ResourceType enum to a human-readable display name (e.g. SCENARIO → Scenario). */
    private static String formatResourceType(ResourceType resourceType) {
        if (resourceType == null) {
            return "Unknown";
        }
        String raw = resourceType.name();
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Serializes the audit log to the console and forwards to the search engine if enabled. */
    private boolean emit(LogEvent doc, Object logLevel, AuditLogType logType) {
        if (AuditLogType.AUDIT == logType) {
            return auditLogTransportDispatcherUtils.dispatch(doc, logLevel);
        }

        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
            String message = "[LOG] json doc: " + json;

            return logTransportDispatcherUtils.dispatch(message, logLevel);
        } catch (Exception e) {
            log.warn("[LOG] Failed to serialize event: {}", e.getMessage(), e);
        }

        return false;
    }

    private boolean emit(String message, Object logLevel, AuditLogType logType) {
        if (AuditLogType.AUDIT == logType) {
            return auditLogTransportDispatcherUtils.dispatch(message, logLevel);
        }
        return logTransportDispatcherUtils.dispatch(message, logLevel);
    }
}
