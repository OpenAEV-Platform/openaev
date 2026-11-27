package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.BulkOperationContext;
import io.openaev.context.TenantContext;
import io.openaev.context.TransactionTenantScope;
import io.openaev.database.model.Base;
import io.openaev.database.model.ResourceType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  /** The entity instance that triggered this event. */
  @JsonIgnore private final Base instance;

  /**
   * The resource whose READ permission governs the entity (see {@link
   * Base#getPermissionResourceId()}), captured while the entity and its parents are still loaded:
   * consumers run after commit, when a deleted entity can no longer be reloaded.
   */
  @JsonIgnore private final String permissionResourceId;

  @JsonIgnore private final ResourceType permissionResourceType;

  private static final String WRITE_TENANT_ATTRIBUTE = BaseEvent.class.getName() + ".writeTenant";

  /**
   * The tenant the change is made in: the one the request declared with {@link
   * #attributeRequestEventsTo} for the type of the entity, else the one tenant the scope of the
   * transaction pins ({@link TransactionTenantScope#pinnedTenant()}), on a request as off it: the
   * row is written there. With neither, {@link TenantContext#getCurrentTenant()} when the event is
   * built, which is the default tenant on a route that names no tenant in its path. Null when the
   * request declared an empty scope for the type with {@link #attributeRequestEventsToOneOf}: the
   * change is made in no tenant. A value that depends on the tenant, such as the organization of a
   * user, is only valid for the consumers of this tenant.
   */
  @JsonIgnore private final String tenantId;

  /**
   * Whether {@link #tenantId} names the tenant the change is made in. False when the transaction
   * scope holds no tenant or several, and no tenant is declared for the type of the entity: the
   * write names no tenant it goes to, and {@link #tenantId} holds the ambient tenant, which need
   * not own the row. That holds on a request (a public route with no tenant in its path, or a
   * caller in several tenants with no selector) and off it (background work across tenants). The
   * stream delivers such an event of a row with no tenant column to the consumers of no tenant.
   * True with no scope written on the transaction.
   */
  @JsonIgnore private final boolean tenantAttributed;

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
    String resolvedPermissionResourceId = data.getId();
    ResourceType resolvedPermissionResourceType = data.getResourceType();
    try {
      resolvedPermissionResourceId = data.getPermissionResourceId();
      resolvedPermissionResourceType = data.getPermissionResourceType();
    } catch (RuntimeException e) {
      // Falls back to the entity itself, which consumers resolve through its own resource type.
      log.debug("Cannot resolve permission resource of {}", data.getClass().getSimpleName(), e);
    }
    Attribution attribution =
        attributionOf(requestAttributes, data, TenantContext.getCurrentTenant());
    this.tenantId = attribution.tenantId();
    this.tenantAttributed = attribution.attributed();
    this.permissionResourceId = resolvedPermissionResourceId;
    this.permissionResourceType = resolvedPermissionResourceType;
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
   * Declares the tenant the writes of the current request go to, for the events of the entities of
   * one type. It comes before the tenant the scope of the transaction pins, for a writer that
   * writes in a tenant its scope does not pin. Does nothing outside a request.
   *
   * <p>The attribution applies to the events of entities that are an instance of {@code
   * entityType}, whenever in the request they are built. The events of every other entity take the
   * tenant of the scope, else the ambient tenant, so a writer names the type whose events carry a
   * value that depends on the tenant, and never moves the events of other entities it touches.
   *
   * @param tenantId the tenant the request writes in; null declares nothing for the type
   * @param entityType the type of the entities whose events are attributed to that tenant
   */
  public static void attributeRequestEventsTo(String tenantId, Class<? extends Base> entityType) {
    if (tenantId != null) {
      attributeRequestEventsToOneOf(List.of(tenantId), entityType);
      return;
    }
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes != null) {
      attributionsOf(requestAttributes).remove(entityType);
    }
  }

  /**
   * Declares the tenants a request reads the roles of a user in, for the events of the entities of
   * one type: the value of such an event that depends on the roles is complete for the consumers of
   * the tenant the event is attributed to, and for no other. The event is attributed to the tenant
   * the events of the request take by default (the one tenant the scope pins, else the ambient
   * tenant) when it is one of {@code tenantIds}, and otherwise to the first of them in the order of
   * their ids. Does nothing outside a request.
   *
   * @param tenantIds the tenants the request reads in
   * @param entityType the type of the entities whose events are attributed
   */
  public static void attributeRequestEventsToOneOf(
      Collection<String> tenantIds, Class<? extends Base> entityType) {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes != null) {
      attributionsOf(requestAttributes).put(entityType, tenantIds.stream().sorted().toList());
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<Class<? extends Base>, List<String>> attributionsOf(
      RequestAttributes requestAttributes) {
    Object attributions =
        requestAttributes.getAttribute(WRITE_TENANT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    if (attributions == null) {
      attributions = new LinkedHashMap<Class<? extends Base>, List<String>>();
      requestAttributes.setAttribute(
          WRITE_TENANT_ATTRIBUTE, attributions, RequestAttributes.SCOPE_REQUEST);
    }
    return (Map<Class<? extends Base>, List<String>>) attributions;
  }

  /** The tenant of an event, and whether the change is known to be made in it. */
  private record Attribution(String tenantId, boolean attributed) {}

  @SuppressWarnings("unchecked")
  private static Attribution attributionOf(
      RequestAttributes requestAttributes, Base data, String ambientTenant) {
    String writeTenant = TransactionTenantScope.pinnedTenant().orElse(ambientTenant);
    // A scope written with no tenant or several names no tenant the write goes to.
    boolean scopeNamesTheWriteTenant =
        !TransactionTenantScope.holdsNoTenant() && !TransactionTenantScope.holdsSeveralTenants();
    if (requestAttributes == null) {
      return new Attribution(writeTenant, scopeNamesTheWriteTenant);
    }
    Object attributions =
        requestAttributes.getAttribute(WRITE_TENANT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    Optional<List<String>> declared =
        attributions == null
            ? Optional.empty()
            : ((Map<Class<? extends Base>, List<String>>) attributions)
                .entrySet().stream()
                    .filter(attribution -> attribution.getKey().isInstance(data))
                    .map(Map.Entry::getValue)
                    .findFirst();
    if (declared.isEmpty()) {
      return new Attribution(writeTenant, scopeNamesTheWriteTenant);
    }
    List<String> tenantIds = declared.get();
    if (tenantIds.isEmpty()) {
      return new Attribution(null, true);
    }
    if (tenantIds.contains(writeTenant)) {
      return new Attribution(writeTenant, true);
    }
    return new Attribution(tenantIds.getFirst(), true);
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
