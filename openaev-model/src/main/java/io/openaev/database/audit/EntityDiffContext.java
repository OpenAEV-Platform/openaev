package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Context that holds field-level diffs for audit enrichment.
 *
 * <p>Storage strategy is request-first:
 *
 * <ul>
 *   <li>If an HTTP request context is available, snapshots and diffs are stored in request
 *       attributes (isolated per request).
 *   <li>Otherwise, a ThreadLocal fallback is used for non-web execution paths.
 * </ul>
 */
public final class EntityDiffContext {

  private static final String REQUEST_ATTR_BEFORE_SNAPSHOTS = "openaev.audit.beforeSnapshots";
  private static final String REQUEST_ATTR_ENTITY_DIFFS = "openaev.audit.entityDiffs";
  private static final String REQUEST_ATTR_CLEANUP_REGISTERED = "openaev.audit.cleanupRegistered";

  private static final ThreadLocal<Map<String, Map<String, Object>>> BEFORE_SNAPSHOTS_TL =
      ThreadLocal.withInitial(LinkedHashMap::new);

  private static final ThreadLocal<Map<String, EntityDiff>> DIFFS_TL =
      ThreadLocal.withInitial(LinkedHashMap::new);

  private static final ThreadLocal<Boolean> CLEANUP_REGISTERED_TL =
      ThreadLocal.withInitial(() -> false);

  private EntityDiffContext() {}

  // -- Before snapshot --

  public static void storeBefore(String entityId, Map<String, Object> snapshot) {
    beforeSnapshots().put(entityId, snapshot);
  }

  public static Map<String, Object> getBefore(String entityId) {
    return beforeSnapshots().get(entityId);
  }

  // -- Diffs --

  public static void storeDiff(String entityId, EntityDiff diff) {
    diffs().put(entityId, diff);
  }

  public static Map<String, EntityDiff> consumeAll() {
    Map<String, EntityDiff> result = new LinkedHashMap<>(diffs());
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
    return Boolean.TRUE.equals(CLEANUP_REGISTERED_TL.get());
  }

  public static void markCleanupRegistered() {
    if (hasRequestContext()) {
      requestAttributes()
          .setAttribute(
              REQUEST_ATTR_CLEANUP_REGISTERED, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
      return;
    }
    CLEANUP_REGISTERED_TL.set(true);
  }

  public static void clear() {
    if (hasRequestContext()) {
      RequestAttributes attrs = requestAttributes();
      attrs.removeAttribute(REQUEST_ATTR_BEFORE_SNAPSHOTS, RequestAttributes.SCOPE_REQUEST);
      attrs.removeAttribute(REQUEST_ATTR_ENTITY_DIFFS, RequestAttributes.SCOPE_REQUEST);
      attrs.removeAttribute(REQUEST_ATTR_CLEANUP_REGISTERED, RequestAttributes.SCOPE_REQUEST);
    } else {
      BEFORE_SNAPSHOTS_TL.get().clear();
      DIFFS_TL.get().clear();
      CLEANUP_REGISTERED_TL.set(false);
    }
  }

  /**
   * Cleanup variant used from transaction-completion hooks.
   *
   * <p>Important: when request-scoped storage is active, keep {@code REQUEST_ATTR_ENTITY_DIFFS}
   * until the controller-level audit aspect consumes them.
   */
  public static void clearAfterTransactionCompletion() {
    if (hasRequestContext()) {
      RequestAttributes attrs = requestAttributes();
      attrs.removeAttribute(REQUEST_ATTR_BEFORE_SNAPSHOTS, RequestAttributes.SCOPE_REQUEST);
      attrs.removeAttribute(REQUEST_ATTR_CLEANUP_REGISTERED, RequestAttributes.SCOPE_REQUEST);
    }

    BEFORE_SNAPSHOTS_TL.get().clear();
    DIFFS_TL.get().clear();
    CLEANUP_REGISTERED_TL.set(false);
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
    return BEFORE_SNAPSHOTS_TL.get();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, EntityDiff> diffs() {
    if (hasRequestContext()) {
      RequestAttributes attrs = requestAttributes();
      Object existing =
          attrs.getAttribute(REQUEST_ATTR_ENTITY_DIFFS, RequestAttributes.SCOPE_REQUEST);
      if (existing instanceof Map<?, ?> map) {
        return (Map<String, EntityDiff>) map;
      }
      Map<String, EntityDiff> created = new LinkedHashMap<>();
      attrs.setAttribute(REQUEST_ATTR_ENTITY_DIFFS, created, RequestAttributes.SCOPE_REQUEST);
      return created;
    }
    return DIFFS_TL.get();
  }

  private static boolean hasRequestContext() {
    return RequestContextHolder.getRequestAttributes() != null;
  }

  private static RequestAttributes requestAttributes() {
    return RequestContextHolder.getRequestAttributes();
  }

  // -- Value types --

  /** Per-entity diff entry, keyed by entity id in the context map. */
  public record EntityDiff(
      @JsonProperty("entity_type") String entityType, String operation, List<Change> changes) {}

  /** Single changed field entry in audit-friendly format. */
  public record Change(
      String field,
      @JsonProperty("old_value") Object oldValue,
      @JsonProperty("new_value") Object newValue) {}
}
