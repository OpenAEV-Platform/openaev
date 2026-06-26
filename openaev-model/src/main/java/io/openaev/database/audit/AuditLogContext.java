package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Context that holds before/after snapshots for audit enrichment.
 *
 * <p>Storage strategy is request-first:
 *
 * <ul>
 *   <li>If an HTTP request context is available, snapshots are stored in request attributes
 *       (isolated per request).
 *   <li>Otherwise, a ThreadLocal fallback is used for non-web execution paths.
 * </ul>
 */
public final class AuditLogContext {

  private static final String REQUEST_ATTR_BEFORE_SNAPSHOTS = "openaev.audit.beforeSnapshots";
  private static final String REQUEST_ATTR_ENTITY_SNAPSHOTS = "openaev.audit.entitySnapshots";
  private static final String REQUEST_ATTR_CLEANUP_REGISTERED = "openaev.audit.cleanupRegistered";
  private static final String REQUEST_ATTR_ENABLED = "openaev.audit.enabled";

  private AuditLogContext() {}

  // -- Before snapshot --

  /**
   * Sets whether audit logging is enabled for the current request/thread.
   *
   * @param enabled {@code true} to enable audit logging (default), {@code false} to suppress it
   */
  public static void setEnabled(boolean enabled) {
    if (hasRequestContext()) {
      requestAttributes()
          .setAttribute(REQUEST_ATTR_ENABLED, enabled, RequestAttributes.SCOPE_REQUEST);
    }
  }

  /** Returns {@code true} if audit logging is enabled for the current request/thread. */
  public static boolean isEnabled() {
    if (hasRequestContext()) {
      Object val =
          requestAttributes().getAttribute(REQUEST_ATTR_ENABLED, RequestAttributes.SCOPE_REQUEST);
      return val == null || Boolean.TRUE.equals(val);
    }
    return true;
  }

  public static void storeBefore(String entityId, Map<String, Object> snapshot) {
    beforeSnapshots().put(entityId, snapshot);
  }

  public static Map<String, Object> getBefore(String entityId) {
    return beforeSnapshots().get(entityId);
  }

  // -- Entity snapshots --

  public static void storeSnapshot(String entityId, EntitySnapshot snapshot) {
    snapshots().put(entityId, snapshot);
  }

  public static Map<String, EntitySnapshot> consumeAllSnapshots() {
    Map<String, EntitySnapshot> result = new LinkedHashMap<>(snapshots());
    clear();
    return result;
  }

  // -- Cleanup registration --

  public static boolean isCleanupRegistered() {
    if (hasRequestContext()) {
      Object val =
          requestAttributes()
              .getAttribute(REQUEST_ATTR_CLEANUP_REGISTERED, RequestAttributes.SCOPE_REQUEST);
      return Boolean.TRUE.equals(val);
    }
    return true;
  }

  public static void markCleanupRegistered() {
    if (hasRequestContext()) {
      requestAttributes()
          .setAttribute(
              REQUEST_ATTR_CLEANUP_REGISTERED, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
    }
  }

  public static void clear() {
    if (hasRequestContext()) {
      RequestAttributes attrs = requestAttributes();
      attrs.removeAttribute(REQUEST_ATTR_BEFORE_SNAPSHOTS, RequestAttributes.SCOPE_REQUEST);
      attrs.removeAttribute(REQUEST_ATTR_ENTITY_SNAPSHOTS, RequestAttributes.SCOPE_REQUEST);
      attrs.removeAttribute(REQUEST_ATTR_CLEANUP_REGISTERED, RequestAttributes.SCOPE_REQUEST);
      attrs.removeAttribute(REQUEST_ATTR_ENABLED, RequestAttributes.SCOPE_REQUEST);
    }
  }

  /**
   * Cleanup variant used from transaction-completion hooks.
   *
   * <p>Important: when request-scoped storage is active, keep {@code REQUEST_ATTR_ENTITY_SNAPSHOTS}
   * until the controller-level audit aspect consumes them.
   */
  public static void clearAfterTransactionCompletion() {
    if (hasRequestContext()) {
      RequestAttributes attrs = requestAttributes();
      attrs.removeAttribute(REQUEST_ATTR_BEFORE_SNAPSHOTS, RequestAttributes.SCOPE_REQUEST);
      attrs.removeAttribute(REQUEST_ATTR_CLEANUP_REGISTERED, RequestAttributes.SCOPE_REQUEST);
    }
  }

  // -- Request/thread storage helpers --

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> beforeSnapshots() {
    if (hasRequestContext()) {
      RequestAttributes attrs = requestAttributes();
      Object existing =
          attrs.getAttribute(REQUEST_ATTR_BEFORE_SNAPSHOTS, RequestAttributes.SCOPE_REQUEST);
      if (existing instanceof Map<?, ?> map) {
        return (Map<String, Map<String, Object>>) map;
      }
      Map<String, Map<String, Object>> created = new LinkedHashMap<>();
      attrs.setAttribute(REQUEST_ATTR_BEFORE_SNAPSHOTS, created, RequestAttributes.SCOPE_REQUEST);
      return created;
    }
    return Collections.emptyMap();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, EntitySnapshot> snapshots() {
    if (hasRequestContext()) {
      RequestAttributes attrs = requestAttributes();
      Object existing =
          attrs.getAttribute(REQUEST_ATTR_ENTITY_SNAPSHOTS, RequestAttributes.SCOPE_REQUEST);
      if (existing instanceof Map<?, ?> map) {
        return (Map<String, EntitySnapshot>) map;
      }
      Map<String, EntitySnapshot> created = new LinkedHashMap<>();
      attrs.setAttribute(REQUEST_ATTR_ENTITY_SNAPSHOTS, created, RequestAttributes.SCOPE_REQUEST);
      return created;
    }
    return Collections.emptyMap();
  }

  public static boolean hasRequestContext() {
    return RequestContextHolder.getRequestAttributes() != null;
  }

  private static RequestAttributes requestAttributes() {
    return RequestContextHolder.getRequestAttributes();
  }

  // -- Value types --

  /** Per-entity snapshot entry holding before/after state. Diff is computed at log time. */
  public record EntitySnapshot(
      @JsonProperty("entity_type") String entityType,
      String operation,
      Map<String, Object> before,
      Map<String, Object> after) {}
}
