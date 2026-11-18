package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.openaev.annotation.AuditDiffTracked;
import io.openaev.annotation.AuditDiffUnloaded;
import io.openaev.config.AuditLogProperties;
import io.openaev.database.model.Base;
import jakarta.annotation.Resource;
import jakarta.persistence.*;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ReflectionUtils;

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

  /** {@link #mapper} with not-loaded {@link AuditDiffUnloaded} collections left unloaded. */
  private volatile ObjectMapper unloadedSafeMapper;

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
      Map<String, Object> before = buildSnapshot(unloadedSafeMapper(), instance);
      // Kept unread: the update snapshot reads their members only if the update touches them.
      before.putAll(notLoadedCollections(instance));
      AuditLogContext.storeBefore(instance.getId(), before);
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
      Map<String, Object> snapshot = buildSnapshot(mapper, instance);
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
   * the snapshot is skipped. An {@link AuditDiffUnloaded} collection is compared only if the update
   * loaded or replaced it, against its members at load.
   */
  @PreUpdate
  void preUpdateForDiff(Object base) {
    if (!shouldCaptureAuditDiff(base)) return;
    Base instance = (Base) base;
    try {
      Map<String, Object> before = AuditLogContext.getBefore(instance.getId());
      if (before == null) return;
      Map<String, Object> after = buildSnapshot(unloadedSafeMapper(), instance);
      Map<String, Object> resolved = resolveNotLoaded(before, after);
      // A later flush of this entity in the transaction compares with the same members at load.
      AuditLogContext.storeBefore(instance.getId(), resolved);
      AuditLogContext.storeSnapshot(
          instance.getId(),
          new AuditLogContext.EntitySnapshot(
              instance.getClass().getSimpleName(), "update", withoutNotLoaded(resolved), after));
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
      Map<String, Object> snapshot = buildSnapshot(mapper, instance);
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
   * The create and delete snapshots pass {@link #mapper}, which loads every collection; the load
   * and update snapshots pass {@link #unloadedSafeMapper()}.
   */
  private Map<String, Object> buildSnapshot(ObjectMapper snapshotMapper, Base entity) {
    // Let serialization failures propagate so callers skip storing/using a broken snapshot.
    return snapshotMapper.convertValue(
        snapshotMapper.valueToTree(entity), new TypeReference<Map<String, Object>>() {});
  }

  private ObjectMapper unloadedSafeMapper() {
    ObjectMapper safeMapper = unloadedSafeMapper;
    if (safeMapper == null) {
      safeMapper =
          mapper
              .copy()
              .registerModule(new SimpleModule().setSerializerModifier(new UnloadedModifier()));
      unloadedSafeMapper = safeMapper;
    }
    return safeMapper;
  }

  /** Wraps the writer of each {@link AuditDiffUnloaded} property. */
  private static final class UnloadedModifier extends BeanSerializerModifier {
    @Override
    public List<BeanPropertyWriter> changeProperties(
        SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> writers) {
      writers.replaceAll(
          writer ->
              writer.getAnnotation(AuditDiffUnloaded.class) != null
                  ? new UnloadedWriter(writer)
                  : writer);
      return writers;
    }
  }

  /**
   * The {@link AuditDiffUnloaded} collections of the entity that are not loaded, by JSON name. The
   * load snapshot keeps them as is, without loading them.
   */
  private static Map<String, Object> notLoadedCollections(Base entity) {
    Map<String, Object> notLoaded = new LinkedHashMap<>();
    ReflectionUtils.doWithFields(
        entity.getClass(),
        field -> {
          ReflectionUtils.makeAccessible(field);
          Object value = field.get(entity);
          if (value != null && !Hibernate.isInitialized(value)) {
            notLoaded.put(jsonName(field), value);
          }
        },
        field -> field.isAnnotationPresent(AuditDiffUnloaded.class));
    return notLoaded;
  }

  private static String jsonName(Field field) {
    JsonProperty property = field.getAnnotation(JsonProperty.class);
    return property != null && !property.value().isEmpty() ? property.value() : field.getName();
  }

  /**
   * Replaces each collection kept unread by the load snapshot with its member ids at load, when the
   * update loaded or replaced it (only such a collection reaches the after snapshot). An untouched
   * one stays as is, never loaded, for a later update in the transaction.
   */
  private static Map<String, Object> resolveNotLoaded(
      Map<String, Object> before, Map<String, Object> after) {
    Map<String, Object> resolved = new LinkedHashMap<>(before);
    before.forEach(
        (name, value) -> {
          if (value instanceof PersistentCollection<?> atLoad && after.containsKey(name)) {
            List<String> ids = idsAtLoad(atLoad);
            if (ids != null) {
              resolved.put(name, ids);
            } else {
              // Members at load unknown: the field stays out of the diff rather than show a guess.
              after.remove(name);
            }
          }
        });
    return resolved;
  }

  /** The before snapshot without the collections still unread, which stay out of the diff. */
  private static Map<String, Object> withoutNotLoaded(Map<String, Object> resolved) {
    Map<String, Object> before = new LinkedHashMap<>(resolved);
    before.values().removeIf(PersistentCollection.class::isInstance);
    return before;
  }

  /**
   * Member ids of the collection as loaded from the database: its stored snapshot ignores the
   * changes made since. A collection replaced without being read is loaded here, so only for an
   * update that replaced it. {@code null} when Hibernate no longer holds that snapshot.
   */
  private static List<String> idsAtLoad(PersistentCollection<?> atLoad) {
    Hibernate.initialize(atLoad);
    Object stored = atLoad.getStoredSnapshot();
    if (stored == null) {
      return null;
    }
    Collection<?> members = stored instanceof Map<?, ?> map ? map.values() : (Collection<?>) stored;
    return members.stream().map(member -> ((Base) member).getId()).toList();
  }

  /** Leaves out a not-loaded collection instead of loading it. */
  private static final class UnloadedWriter extends BeanPropertyWriter {
    UnloadedWriter(BeanPropertyWriter base) {
      super(base);
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov)
        throws Exception {
      Object value = get(bean);
      if (value != null && !Hibernate.isInitialized(value)) {
        return;
      }
      super.serializeAsField(bean, gen, prov);
    }
  }

  private boolean shouldCaptureAuditDiff(Object entity) {
    return auditLogProperties.isEnabled()
        && entity instanceof Base
        && entity.getClass().isAnnotationPresent(AuditDiffTracked.class)
        && AuditLogContext.hasRequestContext();
  }
}
