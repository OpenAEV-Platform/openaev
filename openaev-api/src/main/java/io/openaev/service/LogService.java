package io.openaev.service;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.engine.EngineService;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.ResourceManagerUtils;
import io.openaev.utils.log.LogUtils;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.object.ObjectNormalizationUtils;
import io.openaev.utils.object.ObjectRedactionUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Log service — builds structured {@link LogEvent} events for CRUD and authentication operations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogService {

  @Value("${openaev.audit-logs.service.enabled:false}")
  private boolean auditLogsEnabled;

  private final PreviewFeatureService previewFeatureService;

  private final AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  private final ObjectNormalizationUtils objectNormalizationUtils;
  private final EngineService engineService;

  private final UserService userService;

  // -- Public API --

  /**
   * Returns {@code true} if audit logging is globally enabled and the preview feature is active.
   */
  public boolean isEnabled() {
    return auditLogsEnabled && previewFeatureService.isFeatureEnabled(PreviewFeature.AUDIT_LOG);
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
      String logUUID) {
    if (!isEnabled()) {
      return true;
    }

    try {
      // Build human-readable message
      String message = LogUtils.buildAuthLogMessage(eventScope, eventStatus, provider);
      String eventAccess = LogUtils.getAuthEventAccess();
      LogEvent doc =
          buildBaseAuditLog("authentication", eventStatus, eventAccess, eventScope, logUUID);

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

      return emit(doc, logLevel);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log auth event: {}", e.getMessage(), e);
    }

    return false;
  }

  public boolean logRequestEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String resourceId,
      JsonNode input,
      JsonNode output,
      JsonNode signatureNode,
      Object logLevel,
      String logUUID) {
    try {
      if (!isEnabled()) {
        return true;
      }

      String entityTypeName = formatResourceType(resourceType);
      String displayName = ResourceManagerUtils.extractNameFromSnapshot(input);
      displayName = displayName != null ? displayName : resourceId;
      String message;

      if ("status_change".equals(eventScope)) {
        message = LogUtils.buildStatusChangeMessage(input, entityTypeName, displayName);
      } else {
        message = eventScope + "s " + entityTypeName + " `" + displayName + "`";
      }

      String eventAccess = LogUtils.getDefaultEventAccess();
      LogEvent doc = buildBaseAuditLog("mutation", eventStatus, eventAccess, eventScope, logUUID);
      Map<String, Object> ctx = new LinkedHashMap<>();

      ctx.put("entity_type", entityTypeName);

      if (input != null) {
        // Redacted input
        input = objectNormalizationUtils.normalize(input);
        input = ObjectRedactionUtils.redact(input, resourceType);
        ctx.put("input", toContextValue(input));
      }

      if (output != null) {
        output = objectNormalizationUtils.normalize(output);
        output = ObjectRedactionUtils.redact(output, resourceType);
        ctx.put("output", toContextValue(output));
      }

      doc.getRequestMetadata().setSignature(signatureNode);

      ctx.put("message", message);
      doc.setContextData(ctx);

      return emit(doc, logLevel);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log request event: {}", e.getMessage(), e);
    }

    return false;
  }

  // -- Internal helpers --

  // -- ENTITY CHANGE LOGS --

  /**
   * Logs an entity-change event using the same {@link LogEvent} structure as the audit log. Called
   * by {@link io.openaev.aop.LogEntityChangesAspect} so that all formatting and metadata enrichment
   * live in one place.
   *
   * <p>Root-level fields ({@code event_type}, {@code event_status}, {@code user_id}, {@code
   * tenant_id}, {@code user_metadata}, {@code request_metadata}) are populated via {@link
   * #buildBaseAuditLog}. Entity-specific data ({@code entity_type}, {@code repository}, {@code
   * operation}, {@code message}, {@code before}, {@code after}) go into {@code context_data}.
   *
   * @param repoName simple name of the repository interface (e.g. {@code "UserRepository"})
   * @param operation save / saveAll / delete / deleteById / custom @Modifying method name
   * @param before snapshot before the operation; {@code "[NEW]"} for inserts
   * @param after snapshot after the operation; {@code null} for deletes
   * @param extra optional extra fields merged into {@code context_data} (e.g. ids for @Modifying)
   */
  public void logEntityChangeEvent(
      String repoName,
      String operation,
      Object loglevel,
      Object before,
      Object after,
      Map<String, Object> extra) {
    try {
      String eventScope = resolveEntityChangeScope(operation, before);
      String entityType = repoName.replace("Repository", "");

      LogEvent doc = buildBaseAuditLog("mutation", "success", "administration", eventScope, null);

      // -- context_data --
      Map<String, Object> ctx = new LinkedHashMap<>();
      ctx.put("entity_type", entityType);
      ctx.put("repository", repoName);
      ctx.put("operation", operation);
      ctx.put("message", buildEntityChangeMessage(eventScope, entityType));
      if (extra != null) {
        ctx.putAll(extra);
      }
      if (before != null) {
        ctx.put("before", toContextDataValue(before));
      }
      if (after != null) {
        ctx.put("after", toContextDataValue(after));
      }
      doc.setContextData(ctx);

      emit(doc, loglevel);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log entity change event: {}", e.getMessage(), e);
    }
  }

  /**
   * Maps a repository operation + before-snapshot to a standard {@code event_scope} value aligned
   * with the audit log vocabulary.
   */
  private static String resolveEntityChangeScope(String operation, Object before) {
    return switch (operation) {
      case "delete", "deleteAll", "deleteById" -> "delete";
      case "save", "saveAll" -> "[NEW]".equals(before) ? "create" : "update";
      default -> "update";
    };
  }

  /** Builds a human-readable summary message for an entity-change log entry. */
  private static String buildEntityChangeMessage(String eventScope, String entityType) {
    return switch (eventScope) {
      case "create" -> "creates " + entityType;
      case "delete" -> "deletes " + entityType;
      default -> "updates " + entityType;
    };
  }

  /**
   * Converts entity snapshots to JSON-compatible context values without forcing stringification.
   * Marker values are preserved as plain strings.
   */
  private Object toContextDataValue(Object snapshot) {
    if (snapshot == null) {
      return null;
    }
    if (snapshot instanceof String marker && isSnapshotMarker(marker)) {
      return marker;
    }
    if (snapshot instanceof JsonNode node) {
      return toContextValue(node);
    }
    if (snapshot instanceof Map<?, ?>
        || snapshot instanceof Collection<?>
        || snapshot instanceof Number
        || snapshot instanceof Boolean
        || snapshot instanceof String) {
      return snapshot;
    }
    return engineService.getObjectMapper().convertValue(snapshot, Object.class);
  }

  private static boolean isSnapshotMarker(String snapshot) {
    return "[NEW]".equals(snapshot)
        || "[UNKNOWN_TYPE]".equals(snapshot)
        || snapshot.startsWith("[SNAPSHOT_ERROR:")
        || snapshot.startsWith("[NOT_FOUND:");
  }

  // -- Internal helpers --

  /**
   * Logs a session expiry audit event. Called from the session listener (no HTTP request context).
   *
   * @param userId the user whose session expired
   * @param sessionId the HTTP session ID
   * @param sessionDurationSeconds how long the session was active
   * @param expiryReason "inactivity_timeout" or "explicit_invalidation"
   */
  public boolean logSessionExpiredEvent(
      String userId,
      String sessionId,
      long sessionDurationSeconds,
      String expiryReason,
      String clientIp,
      String userAgent) {
    if (!isEnabled()) {
      return true;
    }

    try {
      String logUUID = UUID.randomUUID().toString();
      LogEvent doc = new LogEvent();
      Instant now = Instant.now();
      doc.setId(logUUID);
      doc.setCreatedAt(now);
      doc.setTimestamp(now);
      doc.setEventType("authentication");
      doc.setEventStatus("success");
      doc.setEventAccess("administration");
      doc.setEventScope("session_expired");
      doc.setUserId(userId);

      // User metadata with session ID
      LogEvent.UserMetadata metadata = new LogEvent.UserMetadata();
      doc.setUserMetadata(metadata);

      metadata.setSessionId(sessionId);
      populateUserEmail(metadata, userId);
      if (clientIp != null) {
        metadata.setIp(clientIp);
      }
      if (userAgent != null) {
        metadata.setUserAgent(userAgent);
      }

      // Context data
      Map<String, Object> ctx = new LinkedHashMap<>();
      ctx.put("session_id", sessionId);
      ctx.put("user_id", userId);
      ctx.put("session_active_duration_seconds", sessionDurationSeconds);
      ctx.put("expiry_reason", expiryReason);
      ctx.put(
          "message",
          "Session expired: active for "
              + sessionDurationSeconds
              + "s, then expired due to "
              + expiryReason.replace("_", " "));
      doc.setContextData(ctx);

      return emit(doc, java.util.logging.Level.INFO);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log session expiry event: {}", e.getMessage(), e);
    }
    return false;
  }

  /** Builds the common part of an {@link LogEvent} with all envelope and user fields populated. */
  private LogEvent buildBaseAuditLog(
      String eventType, String eventStatus, String eventAccess, String eventScope, String logUUID) {
    Instant now = Instant.now();

    if (logUUID == null) {
      logUUID = UUID.randomUUID().toString();
    }

    LogEvent doc = new LogEvent();
    doc.setId(logUUID);
    doc.setCreatedAt(now);
    doc.setTimestamp(now);

    doc.setEventType(eventType);
    doc.setEventStatus(eventStatus);
    doc.setEventAccess(eventAccess);
    doc.setEventScope(eventScope);

    // Request context — prefer the live HTTP request (available in any servlet thread), fall back
    // to ThreadRequestContextHolder which is populated only for async / thread-pool executions.
    LogEvent.RequestMetadata meta = new LogEvent.RequestMetadata();

    HttpServletRequest httpRequest = HttpReqRespUtils.getCurrentRequest();
    if (httpRequest != null) {
      meta.setUrl(httpRequest.getRequestURI());
      meta.setMethod(httpRequest.getMethod());
    } else {
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData requestContextData =
          ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData();
      meta.setUrl(requestContextData != null ? requestContextData.url() : null);
      meta.setMethod(requestContextData != null ? requestContextData.method() : null);
    }
    doc.setRequestMetadata(meta);

    // Tenant context
    doc.setTenantId(TenantContext.getCurrentTenant());

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

  /**
   * Populates the hashed user email on the given metadata if the user exists.
   *
   * @return {@code true} if the email was set, {@code false} otherwise
   */
  private boolean populateUserEmail(LogEvent.UserMetadata meta, String userId) {
    if (userId == null) {
      return false;
    }
    try {
      User user = userService.user(userId);
      if (user != null && user.getEmail() != null) {
        meta.setUserEmail(hashWithSHA256(user.getEmail()));
        return true;
      }
    } catch (Exception e) {
      // User not found or not in a request context — skip email
    }
    return false;
  }

  /** Populates user metadata (email, IP, user agent) on the given audit log document. */
  private void populateUserMetadata(LogEvent doc) {
    LogEvent.UserMetadata meta = new LogEvent.UserMetadata();
    boolean hasData = populateUserEmail(meta, doc.getUserId());

    // HTTP request headers
    HttpServletRequest request = HttpReqRespUtils.getCurrentRequest();

    // Session ID for correlation
    if (request != null) {
      var session = request.getSession(false);
      if (session != null) {
        meta.setSessionId(session.getId());
        hasData = true;
      }
    }

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
        hasData = true;
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
  private boolean emit(LogEvent doc, Object logLevel) {
    return auditLogTransportDispatcherUtils.dispatch(doc, logLevel);
  }

  /** Converts JsonNode payloads to a JSON-compatible Java value while preserving shape. */
  private Object toContextValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }

    ObjectMapper mapper = engineService.getObjectMapper();
    if (node.isObject()) {
      return mapper.convertValue(node, Map.class);
    }
    if (node.isArray()) {
      return mapper.convertValue(node, List.class);
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isNumber()) {
      return node.numberValue();
    }
    if (node.isTextual()) {
      return node.textValue();
    }
    return mapper.convertValue(node, Object.class);
  }
}
