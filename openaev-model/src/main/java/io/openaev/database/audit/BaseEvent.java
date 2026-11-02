package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.BulkOperationContext;
import io.openaev.context.MarkingCtx;
import io.openaev.database.model.Base;
import io.openaev.database.model.ResourceType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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

  /**
   * The resource whose READ permission governs the entity (see {@link
   * Base#getPermissionResourceId()}), captured while the entity and its parents are still loaded:
   * consumers run after commit, when a deleted entity can no longer be reloaded.
   */
  @JsonIgnore private final String permissionResourceId;

  @JsonIgnore private final ResourceType permissionResourceType;

  /**
   * Stands for markings that could not be read when the event was built. No clearance holds it, so
   * the event only reaches the readers who see everything: the stream fails closed.
   */
  static final String UNRESOLVED_MARKING = "unresolved-marking";

  /**
   * The markings a reader must all hold to receive this event (see {@link
   * Base#getStreamMarkings()}), copied when the event is built: consumers run after commit.
   */
  @JsonIgnore private final Set<String> streamMarkings;

  /**
   * The assets the payload lists, with their markings (see {@link
   * Base#getStreamAssetReferences()}), copied when the event is built for the same reason.
   */
  @JsonIgnore private final List<StreamAssetReference> streamAssetReferences;

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
    // Events fired from inside a massive operation are not streamed per entity: connected
    // browsers would refresh once per mutation. They receive aggregated bulk-operation
    // progress events instead (see BulkOperationContext).
    this.listened = data.isListened() && !BulkOperationContext.isActive();
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    this.sessionId = requestAttributes != null ? requestAttributes.getSessionId() : null;
    String resolvedPermissionResourceId = data.getId();
    ResourceType resolvedPermissionResourceType = data.getResourceType();
    try {
      resolvedPermissionResourceId = data.getPermissionResourceId();
      resolvedPermissionResourceType = data.getPermissionResourceType();
    } catch (RuntimeException e) {
      // Falls back to the entity itself, which consumers resolve through its own resource type.
      log.debug("Cannot resolve permission resource of {}", data.getClass().getSimpleName(), e);
    }
    this.permissionResourceId = resolvedPermissionResourceId;
    this.permissionResourceType = resolvedPermissionResourceType;
    Set<String> resolvedStreamMarkings;
    List<StreamAssetReference> resolvedStreamAssetReferences;
    try {
      resolvedStreamMarkings = markingSet(data.getStreamMarkings());
      resolvedStreamAssetReferences =
          data.getStreamAssetReferences().stream()
              .filter(reference -> !reference.markingsByAssetId().isEmpty())
              .toList();
    } catch (RuntimeException e) {
      // The asset an entity depends on may not be loadable here (a lazy proxy during a flush).
      log.debug("Cannot resolve stream markings of {}", data.getClass().getSimpleName(), e);
      resolvedStreamMarkings = Set.of(UNRESOLVED_MARKING);
      resolvedStreamAssetReferences = List.of();
    }
    this.streamMarkings = resolvedStreamMarkings;
    this.streamAssetReferences = resolvedStreamAssetReferences;
    Class<?> baseClass = data.getClass();

    /*
     * Locate the @Id field by walking the WHOLE class hierarchy. Concrete entities can sit
     * several levels below the class that declares the identifier (e.g. the InjectExpectation
     * subclasses: PreventionInjectExpectation -> TechnicalInjectExpectation ->
     * BaseInjectExpectation). The previous implementation only inspected the class and its
     * direct superclass, so 2+ level hierarchies produced events with a null attribute_id /
     * attribute_schema, which crashed the frontend SSE pipeline (normalizr requires a string
     * schema key) and silently dropped every such update. The topmost declaring class wins,
     * preserving the historical "parent overrides child" semantics.
     */
    Field idField = null;
    Class<?> idDeclaringClass = null;
    for (Class<?> current = baseClass;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
          if (idDeclaringClass != null) {
            log.warn(
                "Schema already defined in child class {} but overridden by parent class {} (both define an @Id).",
                idDeclaringClass.getSimpleName(),
                current.getSimpleName());
          }
          idField = field;
          idDeclaringClass = current;
          break;
        }
      }
    }
    if (idField != null) {
      JsonProperty jp = idField.getAnnotation(JsonProperty.class);
      this.attributeId = (jp != null) ? jp.value() : idField.getName();
      this.schema = schemaNameFor(idDeclaringClass);
    }

    /*
     * If no @Id was found, look for @EmbeddedId and derive the attribute ID
     * from the @JsonProperty annotation on the getId() method (Base interface).
     */
    if (this.attributeId == null) {
      for (Field field : baseClass.getDeclaredFields()) {
        if (field.isAnnotationPresent(EmbeddedId.class)) {
          try {
            Method getIdMethod = baseClass.getMethod("getId");
            JsonProperty jp = getIdMethod.getAnnotation(JsonProperty.class);
            this.attributeId = (jp != null) ? jp.value() : "id";
          } catch (NoSuchMethodException e) {
            this.attributeId = "id";
          }
          this.schema = schemaNameFor(baseClass);
          break;
        }
      }
    }

    String streamSchema = data.getStreamSchema();
    if (streamSchema != null) {
      this.schema = streamSchema;
    }
  }

  /**
   * Derives the pluralized schema name from the class declaring the identifier. The explicit JPA
   * entity name is preferred when present ({@code @Entity(name = "InjectExpectation")} on {@code
   * BaseInjectExpectation} yields "injectexpectations"): it is the stable, functional name the
   * frontend keys its entity store on, while the simple class name can drift through refactors
   * (e.g. Base* prefixes introduced when adding JPA subclasses).
   *
   * @param declaringClass the class that declares the {@code @Id} / {@code @EmbeddedId}
   * @return the pluralized, lowercased schema name
   */
  private static String schemaNameFor(Class<?> declaringClass) {
    Entity entity = declaringClass.getAnnotation(Entity.class);
    String className =
        (entity != null && !entity.name().isEmpty())
            ? entity.name().toLowerCase()
            : declaringClass.getSimpleName().toLowerCase();
    return className + (className.endsWith("s") ? "es" : "s");
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

  static Set<String> markingSet(String[] markingIds) {
    return markingIds == null
        ? Set.of()
        : Arrays.stream(markingIds)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Whether a reader with this clearance may receive the event: the clearance allows the markings
   * of the entity (or of the asset it belongs to), and it is not about assets that are all hidden.
   * The listed assets the clearance does not allow are removed from the payload by the stream.
   */
  @JsonIgnore
  public boolean isVisibleUnder(MarkingCtx clearance) {
    return clearance.allows(streamMarkings)
        && streamAssetReferences.stream().noneMatch(ref -> ref.hidesEntityUnder(clearance));
  }

  /** Whether the event depends on markings at all: most do not, and skip the clearance lookup. */
  @JsonIgnore
  public boolean hasMarkingLinks() {
    return !streamMarkings.isEmpty() || !streamAssetReferences.isEmpty();
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
