package io.openaev.config.cache;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.audit_log.AuditResourceDetector.ChildResourceInfo;
import io.openaev.database.model.ResourceType;
import io.openaev.utils.ResourceManagerUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Caches child-resource detection results for the audit logger.
 *
 * <p>Two layers of caching:
 *
 * <ol>
 *   <li><b>Method→ResourceType cache</b>: an in-memory map keyed by the endpoint method signature.
 *       Since a given endpoint always works on the same child {@link ResourceType} (e.g. {@code
 *       updateInject()} always touches {@code INJECT}), this structural fact is cached permanently
 *       — it never becomes stale. This lets us skip the full {@link
 *       ResourceManagerUtils#ENTITY_CLASS_MAP} scan on every subsequent call and go directly to the
 *       known type.
 *   <li><b>Per-request result cache</b>: a Caffeine-backed Spring cache keyed by {@code
 *       methodSignature|resourceType|parentResourceId}. Short TTL (1 min) — evictable when the
 *       child is modified.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditChildResourceCacheManager {

  private final ResourceManagerUtils resourceManagerUtils;

  /**
   * Structural cache: endpoint method signature → child ResourceType. This mapping never changes at
   * runtime (an endpoint always targets the same entity type), so no TTL or eviction needed.
   */
  private final ConcurrentMap<String, ResourceType> methodChildTypeCache =
      new ConcurrentHashMap<>();

  /**
   * Resolves and caches the child resource for the given endpoint/resource context. The {@code
   * methodSignature} key alone is used to look up the known child {@link ResourceType} first; if
   * found, we skip the full entity-class scan. Otherwise the scan runs, and the winning type is
   * stored for future calls.
   *
   * <p>Null results are not cached ({@code unless = "#result == null"}) so a new detection attempt
   * is always made when no child was found previously.
   */
  @Cacheable(
      value = "auditChildResource",
      key = "#methodSignature + '|' + #resourceType + '|' + #parentResourceId",
      unless = "#result == null")
  public ChildResourceInfo resolveChildResource(
      String methodSignature,
      ResourceType resourceType,
      String parentResourceId,
      String[] pathVariableValues) {

    // TODO AUDIT: cache not working bc  parentResourceId == paramValue

    log.warn("pathVariableValues {}", pathVariableValues);
    log.warn("parentResourceId {}", parentResourceId);
    for (String paramValue : pathVariableValues) {
      if (paramValue == null || paramValue.equals(parentResourceId)) {
        continue;
      }

      log.warn(
          "methodSignature "
              + methodSignature
              + "; paramValue "
              + paramValue
              + "; parentResourceId "
              + parentResourceId);
      log.warn("methodSignature {}", methodSignature);
      log.warn("paramValue {}", paramValue);
      ChildResourceInfo childInfo = resolveByKnownTypeFirst(methodSignature, paramValue);
      log.warn("childInfo {}", childInfo);

      if (childInfo != null) {
        return childInfo;
      }
    }
    return null;
  }

  /** Evicts a specific audit-child cache entry (e.g. after the child resource is deleted). */
  @CacheEvict(
      value = "auditChildResource",
      key = "#methodSignature + '|' + #resourceType + '|' + #parentResourceId")
  public void evict(String methodSignature, ResourceType resourceType, String parentResourceId) {
    // eviction only
  }

  /**
   * Attempts to resolve the child entity for {@code resourceId}. Checks the {@link
   * #methodChildTypeCache} first — if we already know the child {@link ResourceType} for this
   * endpoint method, we go straight to it instead of scanning the full entity map. If the fast-path
   * misses (unexpected type), or if no cached type exists yet, falls back to the full scan and
   * stores the discovered type.
   */
  private ChildResourceInfo resolveByKnownTypeFirst(String methodSignature, String resourceId) {
    ResourceType knownType = methodChildTypeCache.get(methodSignature);

    if (knownType != null) {
      // Fast path: try the known type first, skip the full entity-class scan
      ChildResourceInfo childInfo = getChildResourceInfo(knownType, resourceId);
      if (childInfo != null) {
        return childInfo;
      }
    }

    // Full scan: try every ResourceType until one returns a snapshot
    for (Map.Entry<ResourceType, Class<?>> entry :
        ResourceManagerUtils.ENTITY_CLASS_MAP.entrySet()) {
      ChildResourceInfo childInfo = getChildResourceInfo(entry.getKey(), resourceId);
      if (childInfo != null) {
        // Cache the winning ResourceType so future calls skip this scan
        methodChildTypeCache.put(methodSignature, entry.getKey());
        return childInfo;
      }
    }
    return null;
  }

  private ChildResourceInfo getChildResourceInfo(ResourceType resourceType, String resourceId) {
    try {
      JsonNode snapshot = resourceManagerUtils.snapshotResourceEntity(resourceType, resourceId);
      if (snapshot != null) {
        return new ChildResourceInfo(resourceType, resourceId, snapshot);
      }
    } catch (RuntimeException e) {
      // Silently skip — entity does not exist under this ResourceType
    }
    return null;
  }
}
