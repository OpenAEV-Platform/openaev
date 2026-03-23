package io.openaev.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Audit log service — Phase 1: console-only.
 *
 * <p>Builds the full audit event JSON and writes it to a dedicated {@code AUDIT_LOG} logger. This
 * logger is configured at INFO level in {@code logback-spring.xml} independently of the root/package
 * log levels, so audit events always appear even when the application runs at WARN level.
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

  /**
   * Resource types classified as administration — auth events, RBAC changes, platform settings.
   */
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

  @Value("${openaev.audit-logs.enabled:true}")
  private boolean enabled;

  // -- Public API --

  /**
   * Logs a mutation (create/update/delete) audit event to the console.
   *
   * @param eventScope "create", "update", or "delete"
   * @param eventStatus "success" or "error"
   * @param resourceType the resource type from the {@code @AccessControl} annotation
   * @param entityId the resolved entity ID (may be empty for creates before persist)
   * @param input the serialized input DTO (for create/update); null for delete
   * @param oldValue the serialized previous values (for update); null for create/delete
   * @param entityName human-readable entity name for the message (e.g. scenario name)
   * @param parentId the parent entity ID when a child is created within a parent; null otherwise
   */
  public void logMutationEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String entityId,
      JsonNode input,
      JsonNode oldValue,
      String entityName,
      String parentId) {
    if (!enabled) {
      return;
    }
    try {
      String entityTypeName = formatResourceType(resourceType);
      boolean isAdmin = ADMINISTRATION_RESOURCE_TYPES.contains(resourceType);
      String eventAccess = isAdmin ? "administration" : "extended";

      // Build context_data
      ObjectNode contextData = objectMapper.createObjectNode();
      if (entityId != null && !entityId.isEmpty()) {
        contextData.put("id", entityId);
      }
      contextData.put("entity_type", entityTypeName);
      if (parentId != null && !parentId.isEmpty()) {
        contextData.put("parent_id", parentId);
      }
      if (input != null) {
        contextData.set("input", redact(input, entityTypeName));
      }
      if (oldValue != null) {
        contextData.set("old_value", redact(oldValue, entityTypeName));
      }
      String displayName = entityName != null ? entityName : entityId;
      contextData.put("message", eventScope + "s " + entityTypeName + " `" + displayName + "`");

      ObjectNode event = buildBaseEvent("mutation", eventStatus, eventAccess, eventScope);
      event.set("context_data", contextData);

      writeToConsole(event);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log mutation event: {}", e.getMessage(), e);
    }
  }

  /**
   * Logs an authentication audit event to the console.
   *
   * @param eventScope "login", "logout", or "unauthorized"
   * @param eventStatus "success" or "error"
   * @param provider auth provider name (e.g. "local", "auth0", "saml2")
   * @param reason error reason (exception class name); null on success
   */
  public void logAuthEvent(
      String eventScope, String eventStatus, String provider, String reason) {
    if (!enabled) {
      return;
    }
    try {
      ObjectNode contextData = objectMapper.createObjectNode();
      if (provider != null) {
        contextData.put("provider", provider);
      }
      if (reason != null) {
        contextData.put("reason", reason);
      }

      // Build human-readable message
      String message;
      if ("error".equals(eventStatus)) {
        message = "failed " + eventScope + " from provider `" + provider + "`";
      } else if ("logout".equals(eventScope)) {
        message = "logout";
      } else {
        message = eventScope + " from provider `" + provider + "`";
      }
      contextData.put("message", message);

      ObjectNode event = buildBaseEvent("authentication", eventStatus, "administration", eventScope);
      event.set("context_data", contextData);

      writeToConsole(event);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log auth event: {}", e.getMessage(), e);
    }
  }

  // -- Internal helpers --

  /** Builds the top-level event envelope with all common fields. */
  private ObjectNode buildBaseEvent(
      String eventType, String eventStatus, String eventAccess, String eventScope) {
    Instant now = Instant.now();
    String nowStr = now.toString();

    ObjectNode event = objectMapper.createObjectNode();
    event.put("id", UUID.randomUUID().toString());
    event.put("entity_type", "Activity");
    event.put("created_at", nowStr);
    event.put("event_type", eventType);
    event.put("event_status", eventStatus);
    event.put("event_access", eventAccess);
    event.put("event_scope", eventScope);

    // User context
    String userId = resolveUserId();
    if (userId != null) {
      event.put("user_id", userId);
    } else {
      event.putNull("user_id");
    }

    ObjectNode userMetadata = buildUserMetadata();
    if (userMetadata != null) {
      event.set("user_metadata", userMetadata);
    }

    event.put("timestamp", nowStr);
    return event;
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

  /** Builds the user_metadata object from the current HTTP request. */
  private ObjectNode buildUserMetadata() {
    ObjectNode metadata = objectMapper.createObjectNode();

    // User email — denormalized for display
    try {
      String userId = resolveUserId();
      if (userId != null) {
        User user = userService.user(userId);
        if (user != null && user.getEmail() != null) {
          metadata.put("user_email", user.getEmail());
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
        metadata.put("user_agent", userAgent);
      }
      String xForwardedFor = request.getHeader("X-Forwarded-For");
      if (xForwardedFor != null) {
        metadata.put("x_forwarded_for", xForwardedFor);
      }
      String ip = resolveClientIp(request);
      if (ip != null) {
        metadata.put("ip", ip);
      }
    }

    return metadata.isEmpty() ? null : metadata;
  }

  /** Resolves client IP: X-Forwarded-For → X-Real-IP → remoteAddr. */
  private String resolveClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      // X-Forwarded-For may contain multiple IPs — take the first (original client)
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
    String raw = resourceType.name(); // e.g. "PLATFORM_SETTING"
    String[] parts = raw.split("_");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
    }
    return sb.toString(); // e.g. "Platform Setting"
  }

  /** Pretty-prints the event JSON and writes it to the AUDIT_LOG logger. */
  private void writeToConsole(ObjectNode event) {
    try {
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
      AUDIT.info("[AUDIT] {}", json);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to serialize audit event: {}", e.getMessage(), e);
    }
  }
}

