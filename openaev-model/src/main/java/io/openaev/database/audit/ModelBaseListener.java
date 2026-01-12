package io.openaev.database.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Base;
import jakarta.annotation.Resource;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
 */
@Component
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

  /**
   * Handles the post-persist lifecycle callback.
   *
   * <p>Published after a new entity has been persisted to the database.
   *
   * @param base the persisted entity
   */
  @PostPersist
  void postPersist(Object base) {
    Base instance = (Base) base;
    BaseEvent event = new BaseEvent(DATA_PERSIST, instance, mapper);
    appPublisher.publishEvent(event);
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
   * <p>Published before an entity is removed from the database. This allows listeners to capture
   * the full entity state before deletion.
   *
   * @param base the entity being removed
   */
  @PreRemove
  void preRemove(Object base) {
    Base instance = (Base) base;
    appPublisher.publishEvent(new BaseEvent(DATA_DELETE, instance, mapper));
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
}
