package io.openaev.service;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVAnonymous;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.config.audit_log.AuditLogProperties;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.engine.EngineService;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.log.LogUtils;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.object.ObjectNormalizationUtils;
import io.openaev.utils.object.ObjectRedactionUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
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

  private final AuditLogProperties auditLogProperties;

  private final PreviewFeatureService previewFeatureService;

  private final AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  private final ObjectNormalizationUtils objectNormalizationUtils;
  private final EngineService engineService;

  private final UserService userService;

  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  /** Ensures the EE audit-disabled warning is logged only once. */
  private final AtomicBoolean auditEeDisabledWarningLogged = new AtomicBoolean(false);

  // -- Public API --

  public boolean isEnabled() {
    boolean isAuditConfigured =
        auditLogsEnabled && previewFeatureService.isFeatureEnabled(PreviewFeature.AUDIT_LOG);
    if (!isAuditConfigured) {
      return false;
    }

    try {
      boolean isEeActive =
          enterpriseEditionService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo());
      if (!isEeActive && auditEeDisabledWarningLogged.compareAndSet(false, true)) {
        log.error(
            "[AUDIT] Audit logging is configured but inactive - an Enterprise Edition license is required.");
      }
      return isEeActive;
    } catch (Exception e) {
      log.error("[AUDIT] Failed to check enterprise edition license", e);
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
      String logUUID) {
    if (!isEnabled()) {
      return true;
    }

    try {
      // Build human-readable message
      String message = LogUtils.buildAuthLogMessage(eventScope, eventStatus, provider);
      String eventType = LogUtils.getEventType(EventType.AUTHENTICATION);
      String eventAccess = LogUtils.getAuthEventAccess();
      LogEvent doc = buildBaseAuditLog(eventType, eventStatus, eventAccess, eventScope, logUUID);

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
      String displayName = LogUtils.extractNameFromSnapshot(input);
      displayName = displayName != null ? displayName : resourceId;
      String message;

      if ("status_change".equals(eventScope)) {
        message = LogUtils.buildStatusChangeMessage(input, entityTypeName, displayName);
      } else {
        message = eventScope + "s " + entityTypeName + " `" + displayName + "`";
      }

      String eventType = LogUtils.getEventType(EventType.MUTATION);
      String eventAccess = LogUtils.getEventAccess(resourceType);
      LogEvent doc = buildBaseAuditLog(eventType, eventStatus, eventAccess, eventScope, logUUID);
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

    // Request context
    LogEvent.RequestMetadata meta = new LogEvent.RequestMetadata();

    ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData requestContextData =
        ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData();
    String url = requestContextData != null ? requestContextData.url() : null;
    String method = requestContextData != null ? requestContextData.method() : null;

    meta.setUrl(url);
    meta.setMethod(method);
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
    String id = null;

    try {
      OpenAEVPrincipal principal = SessionHelper.currentUser();

      if (principal != null && !(principal instanceof OpenAEVAnonymous)) id = principal.getId();

      if (id == null) {
        ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData
            requestContextData =
                ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData();
        Authentication auth = requestContextData.authentication();

        if (auth != null) {
          Object princ = auth.getPrincipal();

          if (princ instanceof OpenAEVPrincipal user) {
            id = user.getId();
          }
        }
      }
    } catch (Exception e) {
      log.warn("[LOG] Failed to resolve user ID: {}", e.getMessage(), e);
    }

    return id;
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
          String email = hashWithSHA256(user.getEmail());
          meta.setUserEmail(email);
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
