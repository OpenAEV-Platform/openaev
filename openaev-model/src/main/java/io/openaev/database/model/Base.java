package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Transient;
import org.springframework.beans.BeanUtils;

/**
 * Base interface for all database entities in OpenAEV.
 *
 * <p>This interface defines the common contract that all persistent entities must implement,
 * providing:
 *
 * <ul>
 *   <li>Identity management (ID getter/setter)
 *   <li>Access control checks
 *   <li>Update attribute handling
 *   <li>Event listening configuration
 *   <li>RBAC resource type mapping
 * </ul>
 *
 * <p>All entity classes should implement this interface to ensure consistent behavior across the
 * application, particularly for:
 *
 * <ul>
 *   <li>JSON API serialization/deserialization
 *   <li>WebSocket/SSE event publishing
 *   <li>Access control enforcement
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public interface Base {

  /**
   * Returns the unique identifier for this entity.
   *
   * @return the entity ID (typically a UUID string)
   */
  String getId();

  /**
   * Sets the unique identifier for this entity.
   *
   * @param id the entity ID
   */
  void setId(String id);

  /**
   * Checks if a user has access to this entity based on admin status.
   *
   * <p>Default implementation grants access only to administrators. Override in specific entity
   * classes to implement custom access control logic.
   *
   * @param isAdmin whether the user has administrator privileges
   * @return {@code true} if access is granted, {@code false} otherwise
   */
  default boolean isUserHasAccess(final boolean isAdmin) {
    return isAdmin;
  }

  /**
   * Checks if a specific user has access to this entity.
   *
   * @param user the user to check access for
   * @return {@code true} if the user has access, {@code false} otherwise
   */
  default boolean isUserHasAccess(User user) {
    return this.isUserHasAccess(user.isAdmin());
  }

  /**
   * Copies properties from an input object to this entity.
   *
   * <p>Used for partial updates via API requests. Properties are copied using Spring's {@link
   * BeanUtils#copyProperties(Object, Object)}.
   *
   * @param input the source object containing updated values
   */
  @JsonIgnore
  @Transient
  default void setUpdateAttributes(Object input) {
    BeanUtils.copyProperties(input, this);
  }

  /**
   * Indicates whether lifecycle events for this entity should be published.
   *
   * <p>When {@code true}, create/update/delete events will be broadcast via WebSocket and SSE for
   * real-time updates. Override to return {@code false} for entities that should not trigger
   * real-time notifications.
   *
   * @return {@code true} if events should be published, {@code false} otherwise
   */
  default boolean isListened() {
    return true;
  }

  /**
   * Returns the RBAC resource type for this entity.
   *
   * <p>This method links the entity class to a {@link ResourceType} enum value for role-based
   * access control. Override in entity classes to specify the appropriate resource type.
   *
   * @return the resource type for RBAC, defaults to {@link ResourceType#UNKNOWN}
   */
  @JsonIgnore
  default ResourceType getResourceType() {
    return ResourceType.UNKNOWN;
  }
}
