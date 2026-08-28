package io.openaev.database.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.annotation.AuditDiffTracked;
import io.openaev.config.AuditLogProperties;
import io.openaev.database.model.Base;
import jakarta.annotation.Resource;
import jakarta.persistence.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * before/after snapshots and stores computed diffs in {@link AuditLogContext} for enrichment of the
 * audit log. A transaction synchronization is registered to guarantee cleanup of the thread-local
 * context after every transaction, regardless of whether the audit aspect consumed the diffs.
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
 * @see AuditLogContext
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ModelBaseListener {

  /** Event type constant for entity creation. */
  public static final String DATA_PERSIST = "DATA_FETCH_SUCCESS";

  /** Event type constant for entity update. */
  public static final String DATA_UPDATE = "DATA_UPDATE_SUCCESS";

  /** Event type constant for entity deletion. */
  public static final String DATA_DELETE = "DATA_DELETE_SUCCESS";

  @Resource protected ObjectMapper mapper;

  private final AuditLogProperties auditLogProperties;
  private final ApplicationEventPublisher appPublisher;

  // -- Standard lifecycle events --

  /**
   * Captures a "before" snapshot for diff tracking if the entity is {@link AuditDiffTracked}.
   * Called after the entity has been fully loaded (associations resolved).
   */
  @PostLoad
  void postLoad(Object base) {
    if (!shouldCaptureAuditDiff(base)) return;
    Base instance = (Base) base;
    if (instance.getId() == null) return;
    try {
      registerCleanupIfNeeded();
      AuditLogContext.storeBefore(instance.getId(), buildSnapshot(instance));
    } catch (Exception e) {
      log.error(
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

    if (!shouldCaptureAuditDiff(base)) return;
    try {
      registerCleanupIfNeeded();
      Map<String, Object> snapshot = buildSnapshot(instance);
      AuditLogContext.storeSnapshot(
          instance.getId(),
          new AuditLogContext.EntitySnapshot(
              instance.getClass().getSimpleName(), "create", null, snapshot));
    } catch (Exception e) {
      log.error(
          "[AuditDiff] Failed to capture create-snapshot for {}: {}",
          base.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  /**
   * Captures before/after snapshots for {@link AuditDiffTracked} entities just before they are
   * flushed to the database. Diff computation is deferred to the async audit logger.
   *
   * <p>The "before" state is taken from the {@link AuditLogContext} snapshot captured at
   * {@code @PostLoad} time. If no before-snapshot exists (entity was created in this transaction),
   * the snapshot is skipped.
   */
  @PreUpdate
  void preUpdateForDiff(Object base) {
    if (!shouldCaptureAuditDiff(base)) return;
    Base instance = (Base) base;
    try {
      Map<String, Object> before = AuditLogContext.getBefore(instance.getId());
      if (before == null) return;
      Map<String, Object> after = buildSnapshot(instance);
      AuditLogContext.storeSnapshot(
          instance.getId(),
          new AuditLogContext.EntitySnapshot(
              instance.getClass().getSimpleName(), "update", before, after));
    } catch (Exception e) {
      log.error(
          "[AuditDiff] Failed to capture update-snapshot for {}: {}",
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

    if (!shouldCaptureAuditDiff(base)) return;
    try {
      registerCleanupIfNeeded();
      Map<String, Object> snapshot = buildSnapshot(instance);
      AuditLogContext.storeSnapshot(
          instance.getId(),
          new AuditLogContext.EntitySnapshot(
              instance.getClass().getSimpleName(), "delete", snapshot, null));
    } catch (Exception e) {
      log.error(
          "[AuditDiff] Failed to capture delete-snapshot for {}: {}",
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
   * Registers a {@link TransactionSynchronization} to clear {@link AuditLogContext} after the
   * current transaction completes. Registers at most once per transaction.
   */
  private static void registerCleanupIfNeeded() {
    if (!AuditLogContext.isCleanupRegistered()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      AuditLogContext.markCleanupRegistered();
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              AuditLogContext.clearAfterTransactionCompletion();
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

  private boolean shouldCaptureAuditDiff(Object entity) {
    return auditLogProperties.isEnabled()
        && entity instanceof Base
        && entity.getClass().isAnnotationPresent(AuditDiffTracked.class)
        && AuditLogContext.hasRequestContext();
  }
}
