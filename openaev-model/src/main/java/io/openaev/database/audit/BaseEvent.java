package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Base;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Event object representing a database entity lifecycle event.
 *
 * <p>This class encapsulates information about create, update, and delete operations on entities
 * that implement the {@link Base} interface. Events are published via Spring's {@link
 * org.springframework.context.ApplicationEventPublisher} and can be consumed by event listeners for
 * real-time notifications, audit logging, or synchronization with external systems.
 *
 * <p>The event includes:
 *
 * <ul>
 *   <li>Event type (persist, update, delete)
 *   <li>Entity schema (table name)
 *   <li>Serialized entity data
 *   <li>Session context information
 * </ul>
 *
 * @see ModelBaseListener
 * @see IndexEvent
 */
@Slf4j
@Getter
public class BaseEvent implements Cloneable {

  /** The session ID from the current request context, if available. */
  @JsonIgnore private final String sessionId;

  /** The entity instance that triggered this event. */
  @JsonIgnore private final Base instance;

  /** The type of event (e.g., DATA_PERSIST, DATA_UPDATE, DATA_DELETE). */
  @JsonProperty("event_type")
  private String type;

  /** The JSON property name of the entity's ID field. */
  @JsonProperty("attribute_id")
  private String attributeId;

  /** The schema (table) name for the entity. */
  @JsonProperty("attribute_schema")
  private String schema;

  /** The serialized JSON representation of the entity. */
  @JsonProperty("instance")
  private JsonNode instanceData;

  /** Whether this entity should be listened to for real-time updates. */
  @JsonProperty("listened")
  private boolean listened;

  /**
   * Constructs a new base event for the specified entity.
   *
   * @param type the event type (e.g., DATA_PERSIST, DATA_UPDATE, DATA_DELETE)
   * @param data the entity instance that triggered the event
   * @param mapper the Jackson ObjectMapper for JSON serialization
   */
  public BaseEvent(String type, Base data, ObjectMapper mapper) {
    this.type = type;
    this.instance = data;
    this.instanceData = mapper.valueToTree(instance);
    this.listened = data.isListened();
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    this.sessionId = requestAttributes != null ? requestAttributes.getSessionId() : null;
    Class<?> baseClass = data.getClass();

    /*
     * Inspect fields declared directly in the current class.
     * If an @Id is found, initialize the identifier attribute and schema
     * based on the current class name.
     */
    for (Field field : baseClass.getDeclaredFields()) {
      if (field.isAnnotationPresent(Id.class)) {
        JsonProperty jp = field.getAnnotation(JsonProperty.class);
        this.attributeId = (jp != null) ? jp.value() : field.getName();

        String className = baseClass.getSimpleName().toLowerCase();
        this.schema = className + (className.endsWith("s") ? "es" : "s");
        break;
      }
    }

    /*
     * If the class has a parent class, inspect its declared fields.
     * If an @Id is found in the superclass, override the identifier attribute
     * and schema using the parent class definition.
     */
    if (baseClass.getSuperclass() != Object.class) {
      for (Field fieldSC : baseClass.getSuperclass().getDeclaredFields()) {
        if (fieldSC.isAnnotationPresent(Id.class)) {
          if (this.schema != null) {
            log.warn(
                "Schema already defined in child class {} but overridden by parent class {} (both define an @Id).",
                baseClass.getSimpleName(),
                baseClass.getSuperclass().getSimpleName());
          }
          JsonProperty jp = fieldSC.getAnnotation(JsonProperty.class);
          this.attributeId = (jp != null) ? jp.value() : fieldSC.getName();

          String className = baseClass.getSuperclass().getSimpleName().toLowerCase();
          this.schema = className + (className.endsWith("s") ? "es" : "s");
          break;
        }
      }
    }
  }

  /**
   * Sets the event type.
   *
   * @param type the event type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Sets the attribute ID field name.
   *
   * @param attributeId the attribute ID field name
   */
  public void setAttributeId(String attributeId) {
    this.attributeId = attributeId;
  }

  /**
   * Sets the schema (table) name.
   *
   * @param schema the schema name
   */
  public void setSchema(String schema) {
    this.schema = schema;
  }

  /**
   * Sets the serialized instance data.
   *
   * @param instanceData the JSON representation of the entity
   */
  public void setInstanceData(JsonNode instanceData) {
    this.instanceData = instanceData;
  }

  /**
   * Checks whether the user has access to observe this entity's events.
   *
   * @param isAdmin whether the current user has admin privileges
   * @return {@code true} if the user can observe this event, {@code false} otherwise
   */
  @JsonIgnore
  public boolean isUserObserver(final boolean isAdmin) {
    return this.instance.isUserHasAccess(isAdmin);
  }

  /**
   * Creates a shallow copy of this event.
   *
   * @return a cloned copy of this event
   */
  @Override
  public BaseEvent clone() {
    try {
      return (BaseEvent) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }
}
