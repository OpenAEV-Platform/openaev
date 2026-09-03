package io.openaev.service;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.OpenAEVAnonymous;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.config.ThreadPoolTaskLoggerConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.config.cache.LicenseRefreshedEvent;
import io.openaev.context.TenantContext;
import io.openaev.database.model.BannerMessage;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.log.LogEvent;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Log service — builds structured {@link LogEvent} events for CRUD and authentication operations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogService {

  private final AuditLogProperties auditLogProperties;

  private final AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  private final ObjectNormalizationUtils objectNormalizationUtils;
  private final EngineService engineService;

  private final UserService userService;

  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final PlatformSettingsService platformSettingsService;

  /** Ensures the EE audit-disabled warning is logged only once. */
  private final AtomicBoolean auditEeDisabledWarningLogged = new AtomicBoolean(false);

  // -- Public API --

  /**
   * Triggers a banner/license state refresh on startup so the platform banner is accurate before
   * the first audit event fires.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    isEnabled();
  }

  /**
   * Re-evaluates the audit logging state whenever the license cache is refreshed (e.g. after a new
   * Enterprise Edition certificate is saved).
   */
  @EventListener(LicenseRefreshedEvent.class)
  public void onLicenseRefreshed() {
    isEnabled();
  }

  public boolean isEnabled() {
    if (!auditLogProperties.isEnabled()) {
      // Feature not configured — ensure the warning banner is cleared.
      updateBanner(true);
      return false;
    }

    try {
      boolean isEeActive =
          enterpriseEditionService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo());
      if (!isEeActive && auditEeDisabledWarningLogged.compareAndSet(false, true)) {
        log.error(
            "[AUDIT] Audit logging is configured but inactive - an Enterprise Edition license is required.");
      } else if (isEeActive) {
        // License now valid — reset so the warning fires again if it lapses later.
        auditEeDisabledWarningLogged.set(false);
      }
      updateBanner(isEeActive);
      return isEeActive;
    } catch (Exception e) {
      log.error("[AUDIT] Failed to check enterprise edition license", e);
    }
    return false;
  }

  /**
   * Updates the {@link BannerMessage.BANNER_KEYS#AUDIT_LOG_NO_ENTERPRISE_LICENSE} platform banner.
   *
   * @param shouldClean {@code true} → clear the banner; {@code false} → show the banner.
   */
  private void updateBanner(boolean shouldClean) {
    if (shouldClean) {
      platformSettingsService.cleanMessage(
          BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE);
    } else {
      platformSettingsService.errorMessage(
          BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE);
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
      String eventType = EventType.AUTHENTICATION.name().toLowerCase();
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
      JsonNode entityDiffsNode,
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
        message = LogUtils.buildRequestLogMessage(eventScope, entityTypeName, displayName);
      }

      String eventType = EventType.MUTATION.name().toLowerCase();
      String eventAccess = LogUtils.getEventAccess(resourceType);
      LogEvent doc = buildBaseAuditLog(eventType, eventStatus, eventAccess, eventScope, logUUID);
      Map<String, Object> ctx = new LinkedHashMap<>();

      ctx.put("entity_type", entityTypeName);

      if (input != null) {
        // Redact before normalizing: the normalization size-cap fallback serializes the tree into
        // a scalar "preview" that field-name-based redaction cannot scrub afterwards.
        input = ObjectRedactionUtils.redact(input, resourceType);
        input = objectNormalizationUtils.normalize(input);
        ctx.put("input", toContextValue(input));
      }

      if (output != null) {
        output = ObjectRedactionUtils.redact(output, resourceType);
        output = objectNormalizationUtils.normalize(output);
        ctx.put("output", toContextValue(output));
      }

      // Enrich with entity-level diffs captured by @AuditDiffTracked listeners.
      if (entityDiffsNode != null && !entityDiffsNode.isEmpty()) {
        entityDiffsNode = ObjectRedactionUtils.redact(entityDiffsNode, resourceType);
        ctx.put("entity_diffs", toContextValue(entityDiffsNode));
      }

      if (signatureNode != null) {
        signatureNode = ObjectRedactionUtils.redact(signatureNode, resourceType);
        signatureNode = objectNormalizationUtils.normalize(signatureNode);
        doc.getRequestMetadata().setSignature(signatureNode);
      }

      ctx.put("message", message);
      doc.setContextData(ctx);

      return emit(doc, logLevel);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log request event: {}", e.getMessage(), e);
    }

    return false;
  }

  // -- Internal helpers --

  /**
   * Logs a generic audit event. This is the single transport method for all event types — both
   * legacy utility methods ({@code logAuthEvent}, {@code logRequestEvent}) and the new generic
   * entry point ({@code AuditLogger.logEvent}) delegate here.
   *
   * @param event the audit event descriptor
   * @param logLevel the log level for console emission
   * @param logUUID the unique ID for this audit entry
   * @return {@code true} if the event was successfully emitted
   */
  public boolean logGenericEvent(AuditEvent event, Object logLevel, String logUUID) {
    if (!isEnabled()) {
      return true;
    }

    try {
      String eventType = event.getEventType().name().toLowerCase();
      String eventStatus = event.getEventStatus().name().toLowerCase();
      String eventScope = event.getEventScope().name().toLowerCase();

      // For MUTATION events, resolve access based on ResourceType
      String eventAccess;
      ResourceType resourceType = event.getResourceType();
      if (event.getEventType() == EventType.MUTATION && resourceType != null) {
        eventAccess = LogUtils.getEventAccess(resourceType);
      } else {
        eventAccess = resolveEventAccess(event);
      }

      LogEvent doc = buildBaseAuditLog(eventType, eventStatus, eventAccess, eventScope, logUUID);

      Map<String, Object> ctx =
          new LinkedHashMap<>(event.getContextData() != null ? event.getContextData() : Map.of());

      // Capture raw JsonNode input BEFORE processMutationContext converts it to Map
      JsonNode rawInputNode =
          (event.getEventType() == EventType.MUTATION && ctx.get("input") instanceof JsonNode jn)
              ? jn
              : null;

      // MUTATION events: normalize, redact, and build message from input/output
      if (event.getEventType() == EventType.MUTATION) {
        processMutationContext(ctx, resourceType, eventScope, doc);
      }

      if (event.getMessage() != null) {
        ctx.put("message", event.getMessage());
      } else if (event.getEventType() == EventType.MUTATION) {
        // Build message from mutation context if not explicitly provided
        String entityTypeName = resourceType != null ? formatResourceType(resourceType) : null;
        String displayName =
            rawInputNode != null ? LogUtils.extractNameFromSnapshot(rawInputNode) : null;
        displayName = displayName != null ? displayName : event.getResourceId();

        String message;
        if ("status_change".equals(eventScope)) {
          message = LogUtils.buildStatusChangeMessage(rawInputNode, entityTypeName, displayName);
        } else {
          message = LogUtils.buildRequestLogMessage(eventScope, entityTypeName, displayName);
        }
        ctx.put("message", message);
      }

      if (resourceType != null && !ctx.containsKey("entity_type")) {
        ctx.put("entity_type", formatResourceType(resourceType));
      }
      if (event.getResourceId() != null && !ctx.containsKey("resource_id")) {
        ctx.put("resource_id", event.getResourceId());
      }

      // Entity diffs — normalize and redact
      if (event.getEntityDiffs() != null && !event.getEntityDiffs().isEmpty()) {
        JsonNode redactedDiffs = ObjectRedactionUtils.redact(event.getEntityDiffs(), resourceType);
        ctx.put("entity_diffs", toContextValue(redactedDiffs));
      }

      doc.setContextData(ctx);

      // For SYSTEM-origin: skip user metadata population (no servlet context)
      if (event.getOrigin() == AuditEventOrigin.SYSTEM) {
        doc.setUserId(null);
        doc.setUserMetadata(null);
      }

      return emit(doc, logLevel);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to log generic event: {}", e.getMessage(), e);
    }
    return false;
  }

  /**
   * Processes MUTATION-specific contextData entries: redacts and normalizes input/output JsonNodes,
   * and extracts the signature into request metadata.
   *
   * <p>Redaction always runs before normalization: when a tree is still oversized after truncation,
   * the normalization fallback serializes it into a scalar {@code preview} field that field-name
   * based redaction cannot scrub afterwards. Redacting first guarantees the preview can only ever
   * contain already-redacted data.
   */
  private void processMutationContext(
      Map<String, Object> ctx, ResourceType resourceType, String eventScope, LogEvent doc) {
    // Redact and normalize input
    Object inputObj = ctx.get("input");
    if (inputObj instanceof JsonNode inputNode && !inputNode.isNull()) {
      inputNode = ObjectRedactionUtils.redact(inputNode, resourceType);
      inputNode = objectNormalizationUtils.normalize(inputNode);
      ctx.put("input", toContextValue(inputNode));
    }

    // Redact and normalize output
    Object outputObj = ctx.get("output");
    if (outputObj instanceof JsonNode outputNode && !outputNode.isNull()) {
      outputNode = ObjectRedactionUtils.redact(outputNode, resourceType);
      outputNode = objectNormalizationUtils.normalize(outputNode);
      ctx.put("output", toContextValue(outputNode));
    }

    // Extract signature into request metadata. Normalization is what enforces the max event size:
    // without it a large payload reaching the signature node (e.g. a @RequestPart DTO) is logged
    // uncapped and can exhaust the heap.
    Object signatureObj = ctx.remove("signature");
    if (signatureObj instanceof JsonNode signatureNode && !signatureNode.isNull()) {
      JsonNode redactedSignature = ObjectRedactionUtils.redact(signatureNode, resourceType);
      JsonNode normalizedSignature = objectNormalizationUtils.normalize(redactedSignature);
      doc.getRequestMetadata().setSignature(normalizedSignature);
    }
  }

  /** Resolves the event access level based on the event type. */
  private String resolveEventAccess(AuditEvent event) {
    if (event.getEventType() == EventType.AUTHENTICATION) {
      return "administration";
    }
    return "extended";
  }

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
          "message", LogUtils.buildSessionExpiredLogMessage(sessionDurationSeconds, expiryReason));
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

    // Session ID for correlation — always comes from RequestContextData captured on servlet thread,
    // since populateUserMetadata runs on the async taskLoggerExecutor thread.
    ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData rcd =
        ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData();
    if (rcd != null && rcd.sessionId() != null) {
      meta.setSessionId(rcd.sessionId());
      hasData = true;
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
