package io.openaev.database.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.annotation.AuditDiffTracked;
import io.openaev.database.model.Base;
import jakarta.annotation.Resource;
import jakarta.persistence.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * JPA entity listener that publishes lifecycle events for database entities.
 *
 * <p>This listener is automatically invoked by JPA whenever an entity implementing {@link Base} is
 * persisted, updated, or removed. It publishes corresponding Spring application events that can be
 * consumed by other components for:
 *
 * <ul>
 *   <li>Real-time notifications via WebSocket or SSE
 *   <li>Audit logging
 *   <li>Search index synchronization
 *   <li>Cache invalidation
 * </ul>
 *
 * <p>When an entity is annotated with {@link AuditDiffTracked}, this listener also captures
 * before/after snapshots and stores computed diffs in {@link EntityDiffContext} for enrichment of
 * the audit log. A transaction synchronization is registered to guarantee cleanup of the thread-
 * local context after every transaction, regardless of whether the audit aspect consumed the diffs.
 *
 * <p>To enable this listener on an entity, use the {@link EntityListeners} annotation:
 *
 * <pre>{@code
 * @Entity
 * @EntityListeners(ModelBaseListener.class)
 * public class MyEntity implements Base {
 *     // ...
 * }
 * }</pre>
 *
 * @see BaseEvent
 * @see IndexEvent
 * @see EntityDiffContext
 */
@Component
@Slf4j
public class ModelBaseListener {

  /** Event type constant for entity creation. */
  public static final String DATA_PERSIST = "DATA_FETCH_SUCCESS";

  /** Event type constant for entity update. */
  public static final String DATA_UPDATE = "DATA_UPDATE_SUCCESS";

  /** Event type constant for entity deletion. */
  public static final String DATA_DELETE = "DATA_DELETE_SUCCESS";

  @Resource protected ObjectMapper mapper;

  private ApplicationEventPublisher appPublisher;

  /**
   * Sets the application event publisher for broadcasting entity lifecycle events.
   *
   * @param applicationEventPublisher the Spring event publisher
   */
  @Autowired
  public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.appPublisher = applicationEventPublisher;
  }

  // -- Standard lifecycle events --

  /**
   * Captures a "before" snapshot for diff tracking if the entity is {@link AuditDiffTracked}.
   * Called after the entity has been fully loaded (associations resolved).
   */
  @PostLoad
  void postLoad(Object base) {
    if (!isAuditDiffTracked(base)) return;
    Base instance = (Base) base;
    if (instance.getId() == null) return;
    try {
      registerCleanupIfNeeded();
      EntityDiffContext.storeBefore(instance.getId(), buildSnapshot(instance));
    } catch (Exception e) {
      log.debug(
          "[AuditDiff] Failed to store before-snapshot for {}: {}",
          base.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  /**
   * Handles the post-persist lifecycle callback.
   *
   * <p>Published after a new entity has been persisted to the database. For {@link
   * AuditDiffTracked} entities, stores a "create" diff with the full entity snapshot as context.
   *
   * @param base the persisted entity
   */
  @PostPersist
  void postPersist(Object base) {
    Base instance = (Base) base;
    BaseEvent event = new BaseEvent(DATA_PERSIST, instance, mapper);
    appPublisher.publishEvent(event);

    if (!isAuditDiffTracked(base)) return;
    try {
      registerCleanupIfNeeded();
      Map<String, Object> snapshot = buildSnapshot(instance);
      List<EntityDiffContext.Change> changes =
          snapshot.entrySet().stream()
              .map(e -> new EntityDiffContext.Change(e.getKey(), null, e.getValue()))
              .toList();
      EntityDiffContext.storeDiff(
          instance.getId(),
          new EntityDiffContext.EntityDiff(instance.getClass().getSimpleName(), "create", changes));
    } catch (Exception e) {
      log.debug(
          "[AuditDiff] Failed to capture create-diff for {}: {}",
          base.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  /**
   * Computes and stores a field-level diff for {@link AuditDiffTracked} entities just before they
   * are flushed to the database.
   *
   * <p>The "before" state is taken from the {@link EntityDiffContext} snapshot captured at
   * {@code @PostLoad} time. If no before-snapshot exists (entity was created in this transaction),
   * the diff is skipped.
   */
  @PreUpdate
  void preUpdateForDiff(Object base) {
    if (!isAuditDiffTracked(base)) return;
    Base instance = (Base) base;
    try {
      Map<String, Object> before = EntityDiffContext.getBefore(instance.getId());
      if (before == null) return;
      Map<String, Object> after = buildSnapshot(instance);
      List<EntityDiffContext.Change> changes = computeChanges(before, after);
      if (!changes.isEmpty()) {
        EntityDiffContext.storeDiff(
            instance.getId(),
            new EntityDiffContext.EntityDiff(
                instance.getClass().getSimpleName(), "update", changes));
      }
    } catch (Exception e) {
      log.debug(
          "[AuditDiff] Failed to compute update-diff for {}: {}",
          base.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  /**
   * Handles the post-update lifecycle callback.
   *
   * <p>Published after an existing entity has been updated in the database.
   *
   * @param base the updated entity
   */
  @PostUpdate
  void postUpdate(Object base) {
    Base instance = (Base) base;
    BaseEvent event = new BaseEvent(DATA_UPDATE, instance, mapper);
    appPublisher.publishEvent(event);
  }

  /**
   * Handles the pre-remove lifecycle callback.
   *
   * <p>Published before an entity is removed from the database. For {@link AuditDiffTracked}
   * entities, stores a "delete" diff with the full entity snapshot as context (the state before
   * deletion).
   *
   * @param base the entity being removed
   */
  @PreRemove
  void preRemove(Object base) {
    Base instance = (Base) base;
    appPublisher.publishEvent(new BaseEvent(DATA_DELETE, instance, mapper));

    if (!isAuditDiffTracked(base)) return;
    try {
      registerCleanupIfNeeded();
      Map<String, Object> snapshot = buildSnapshot(instance);
      List<EntityDiffContext.Change> changes =
          snapshot.entrySet().stream()
              .map(e -> new EntityDiffContext.Change(e.getKey(), e.getValue(), null))
              .toList();
      EntityDiffContext.storeDiff(
          instance.getId(),
          new EntityDiffContext.EntityDiff(instance.getClass().getSimpleName(), "delete", changes));
    } catch (Exception e) {
      log.debug(
          "[AuditDiff] Failed to capture delete-diff for {}: {}",
          base.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  /**
   * Handles the post-remove lifecycle callback.
   *
   * <p>Published after an entity has been removed from the database. This triggers an {@link
   * IndexEvent} to synchronize the search index. Note that search index create/update operations
   * are handled by a separate scheduled job.
   *
   * @param base the removed entity
   */
  @PostRemove
  void postRemove(Object base) {
    Base instance = (Base) base;
    appPublisher.publishEvent(new IndexEvent(DATA_DELETE, instance.getId()));
  }

  // -- Diff helpers --

  /**
   * Registers a {@link TransactionSynchronization} to clear {@link EntityDiffContext} after the
   * current transaction completes. Registers at most once per transaction.
   */
  private static void registerCleanupIfNeeded() {
    if (!EntityDiffContext.isCleanupRegistered()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      EntityDiffContext.markCleanupRegistered();
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              EntityDiffContext.promoteDiffsToRequestAttributes();
              EntityDiffContext.clearAfterTransactionCompletion();
            }
          });
    }
  }

  /**
   * Serializes an entity to a {@code Map<String,Object>} using the same Jackson configuration as
   * the REST API (respects {@code @JsonIgnore}, {@code @JsonProperty}, custom serializers, etc.).
   */
  private Map<String, Object> buildSnapshot(Base entity) {
    // Let serialization failures propagate so callers skip storing/using a broken snapshot.
    return mapper.convertValue(
        mapper.valueToTree(entity), new TypeReference<Map<String, Object>>() {});
  }

  /**
   * Computes a field-level change list between two snapshots in audit-friendly format.
   *
   * @return a list of changes {@code [{field, old_value, new_value}]} containing only changed
   *     fields
   */
  private static List<EntityDiffContext.Change> computeChanges(
      Map<String, Object> before, Map<String, Object> after) {
    List<EntityDiffContext.Change> changes = new ArrayList<>();
    Set<String> allKeys = new LinkedHashSet<>(after.keySet());
    allKeys.addAll(before.keySet());
    for (String key : allKeys) {
      Object beforeVal = before.get(key);
      Object afterVal = after.get(key);
      if (!Objects.equals(normalizeForComparison(beforeVal), normalizeForComparison(afterVal))) {
        changes.add(new EntityDiffContext.Change(key, beforeVal, afterVal));
      }
    }
    return changes;
  }

  /**
   * Normalizes a snapshot value for equality comparison. Lists (e.g., user IDs, role IDs) are
   * sorted to avoid false positives caused by insertion-order differences.
   */
  private static String normalizeForComparison(Object val) {
    if (val == null) return null;
    if (val instanceof Collection<?> collection) {
      return collection.stream().map(Object::toString).sorted().collect(Collectors.joining(","));
    }
    if (val instanceof Map<?, ?> map) {
      return map.entrySet().stream()
          .sorted(Map.Entry.comparingByKey((a, b) -> a.toString().compareTo(b.toString())))
          .map(entry -> entry.getKey() + "=" + normalizeForComparison(entry.getValue()))
          .collect(Collectors.joining("|"));
    }
    return val.toString();
  }

  private static boolean isAuditDiffTracked(Object entity) {
    return entity instanceof Base && entity.getClass().isAnnotationPresent(AuditDiffTracked.class);
  }
}
