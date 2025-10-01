package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Transient;
import org.springframework.beans.BeanUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
public interface Base {
  String getId();

  void setId(String id);

  default boolean isUserHasAccess(final boolean isAdmin) {
    return isAdmin;
  }

  default boolean isUserHasAccess(User user) {
    return this.isUserHasAccess(user.isAdmin());
  }

  @JsonIgnore
  @Transient
  default void setUpdateAttributes(Object input) {
    BeanUtils.copyProperties(input, this);
  }

  default boolean isListened() {
    return true;
  }

  /**
   * Used to link a class to an RBAC ResourceType which is useful when managing permission on the
   * stream
   *
   * @return
   */
  @JsonIgnore
  default ResourceType getResourceType() {
    return ResourceType.UNKNOWN;
  }
}
