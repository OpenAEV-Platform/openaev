package io.openaev.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.engine.model.auditlog.EsAuditLog;
import io.openaev.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Audit log service — builds structured {@link EsAuditLog} events for CRUD and authentication
 * operations.
 *
 * <p>Events are always written to a dedicated {@code AUDIT_LOG} logger (console output). When
 * {@code openaev.audit-logs.opensearch.enabled=true}, events are additionally indexed into the
 * search engine (OpenSearch / Elasticsearch) via {@link AuditOpenSearchService} for subsequent
 * querying through the {@code /api/audit-logs/search} endpoint.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

  /**
   * Dedicated audit logger — configured in logback-spring.xml with its own appender so it is not
   * suppressed by the root or io.openaev log level settings.
   */
  private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT_LOG");

  /** Standard class logger for internal warnings/errors. */
  private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

  private static final String REDACTED = "*** Redacted ***";

  /** Fields whose values are replaced with {@link #REDACTED} before logging. */
  private static final Set<String> SENSITIVE_FIELDS =
      Set.of("password", "token", "secret", "newpassword", "apikey", "credential");

  /** Fields redacted only when the entity type is User (PII protection). */
  private static final Set<String> USER_PII_FIELDS = Set.of("name", "user_email");

  /** Resource types classified as administration — auth events, RBAC changes, platform settings. */
  private static final Set<ResourceType> ADMINISTRATION_RESOURCE_TYPES =
      Set.of(
          ResourceType.USER,
          ResourceType.USER_GROUP,
          ResourceType.GROUP_ROLE,
          ResourceType.PLATFORM_SETTING,
          ResourceType.TENANT,
          ResourceType.ORGANIZATION);

  private final ObjectMapper objectMapper;
  private final UserService userService;

  /**
   * Optional search-engine indexing service — present only when {@code
   * openaev.audit-logs.opensearch.enabled=true}.
   */
  private Optional<AuditOpenSearchService> auditOpenSearchService = Optional.empty();

  @Autowired(required = false)
  public void setAuditOpenSearchService(AuditOpenSearchService auditOpenSearchService) {
    this.auditOpenSearchService = Optional.ofNullable(auditOpenSearchService);
  }

  @Value("${openaev.audit-logs.enabled:true}")
  private boolean enabled;

  // -- Public API --

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
  public void logMutationEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String entityId,
      JsonNode input,
      JsonNode oldValue,
      String entityName,
      String parentId,
      String sourceId) {
    if (!enabled) {
      return;
    }
    try {
      String entityTypeName = formatResourceType(resourceType);
      boolean isAdmin = ADMINISTRATION_RESOURCE_TYPES.contains(resourceType);

      String displayName = entityName != null ? entityName : entityId;
      String message;
      if ("status_change".equals(eventScope)) {
        message = buildStatusChangeMessage(input, entityTypeName, displayName);
      } else {
        message = eventScope + "s " + entityTypeName + " `" + displayName + "`";
      }

      EsAuditLog doc =
          buildBaseAuditLog(
              "mutation", eventStatus, isAdmin ? "administration" : "extended", eventScope);

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
        ctx.put("input", objectMapper.convertValue(redact(input, entityTypeName), Map.class));
      }
      if (oldValue != null) {
        ctx.put(
            "old_value", objectMapper.convertValue(redact(oldValue, entityTypeName), Map.class));
      }
      ctx.put("message", message);
      doc.setContextData(ctx);

      emit(doc);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log mutation event: {}", e.getMessage(), e);
    }
  }

  /**
   * Logs an authentication audit event.
   *
   * @param eventScope "login", "logout", or "unauthorized"
   * @param eventStatus "success" or "error"
   * @param provider auth provider name (e.g. "local", "auth0", "saml2")
   * @param reason error reason (exception class name); null on success
   */
  public void logAuthEvent(String eventScope, String eventStatus, String provider, String reason) {
    if (!enabled) {
      return;
    }
    try {
      // Build human-readable message
      String message;
      if ("error".equals(eventStatus)) {
        message = "failed " + eventScope + " from provider `" + provider + "`";
      } else if ("logout".equals(eventScope)) {
        message = "logout";
      } else {
        message = eventScope + " from provider `" + provider + "`";
      }

      EsAuditLog doc =
          buildBaseAuditLog("authentication", eventStatus, "administration", eventScope);

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

      emit(doc);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log auth event: {}", e.getMessage(), e);
    }
  }

  // -- Internal helpers --

  /**
   * Builds a human-readable message for status_change events. Handles three cases:
   *
   * <ul>
   *   <li>Exercise status change: input has {@code exercise_status}
   *   <li>Scenario instant launch: input has {@code action=launch}
   *   <li>Scenario recurrence update: input has {@code scenario_recurrence} fields
   * </ul>
   */
  private String buildStatusChangeMessage(
      JsonNode input, String entityTypeName, String displayName) {
    if (input == null) {
      return "changes status of " + entityTypeName + " `" + displayName + "`";
    }
    if (input.has("exercise_status")) {
      String newStatus = input.get("exercise_status").asText().toLowerCase();
      return "changes status of "
          + entityTypeName
          + " `"
          + displayName
          + "` to `"
          + newStatus
          + "`";
    }
    if (input.has("action") && "launch".equals(input.get("action").asText())) {
      return "launches " + entityTypeName + " `" + displayName + "`";
    }
    if (input.has("scenario_recurrence")) {
      return "updates recurrence of " + entityTypeName + " `" + displayName + "`";
    }
    return "changes status of " + entityTypeName + " `" + displayName + "`";
  }

  /**
   * Builds the common part of an {@link EsAuditLog} with all envelope and user fields populated.
   */
  private EsAuditLog buildBaseAuditLog(
      String eventType, String eventStatus, String eventAccess, String eventScope) {
    Instant now = Instant.now();

    EsAuditLog doc = new EsAuditLog();
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
  private void populateUserMetadata(EsAuditLog doc) {
    EsAuditLog.UserMetadata meta = new EsAuditLog.UserMetadata();
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
    HttpServletRequest request = getCurrentRequest();
    if (request != null) {
      String userAgent = request.getHeader("User-Agent");
      if (userAgent != null) {
        meta.setUserAgent(userAgent);
        hasData = true;
      }
      String xff = request.getHeader("X-Forwarded-For");
      if (xff != null && !xff.isEmpty()) {
        meta.setXForwardedFor(xff);
      }
      String ip = resolveClientIp(request);
      if (ip != null) {
        meta.setIp(ip);
        hasData = true;
      }
    }

    if (hasData) {
      doc.setUserMetadata(meta);
    }
  }

  /** Resolves client IP: X-Forwarded-For → X-Real-IP → remoteAddr. */
  private String resolveClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      return xff.split(",")[0].trim();
    }
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp;
    }
    return request.getRemoteAddr();
  }

  private HttpServletRequest getCurrentRequest() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      return attrs != null ? attrs.getRequest() : null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Redacts sensitive field values in a JSON tree. Operates on a deep copy — the original is never
   * modified.
   */
  private JsonNode redact(JsonNode node, String entityTypeName) {
    if (node == null || node.isNull()) {
      return node;
    }
    ObjectNode copy = node.deepCopy();
    boolean isUserEntity = "User".equalsIgnoreCase(entityTypeName);
    copy.properties()
        .forEach(
            entry -> {
              String fieldName = entry.getKey().toLowerCase();
              if (SENSITIVE_FIELDS.contains(fieldName)) {
                copy.put(entry.getKey(), REDACTED);
              } else if (isUserEntity && USER_PII_FIELDS.contains(fieldName)) {
                copy.put(entry.getKey(), REDACTED);
              }
            });
    return copy;
  }

  /** Converts a ResourceType enum to a human-readable display name (e.g. SCENARIO → Scenario). */
  static String formatResourceType(ResourceType resourceType) {
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
  private void emit(EsAuditLog doc) {
    try {
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
      AUDIT.info("[AUDIT] {}", json);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to serialize audit event: {}", e.getMessage(), e);
    }
    auditOpenSearchService.ifPresent(service -> service.indexAuditEvent(doc));
  }
}
