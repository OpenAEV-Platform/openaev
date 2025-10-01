package io.openaev.database.model;

import java.lang.annotation.*;

/**
 * Annotation to mark a resource type that can be granted permissions. This is used to link a class
 * to an RBAC ResourceType.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Grantable {
  /**
   * @return the GRANT_RESOURCE_TYPE associated with the annotated class
   */
  Grant.GRANT_RESOURCE_TYPE value();
}
