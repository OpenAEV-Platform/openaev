package io.openaev.aop;

import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RBAC {

  /**
   * The ID of the resource manipulated by the target endpoint. This is used to determine the RBAC
   * grants. If not specified, it defaults to an empty string, which means no specific resource ID
   * is required.
   */
  String resourceId() default "";

  /**
   * The action performed on the resource by the target endpoint. This is used to determine the RBAC
   * permissions. If not specified, it defaults to Action.SKIP_RBAC, which means no RBAC check will
   * be performed.
   */
  Action actionPerformed() default Action.SKIP_RBAC;

  /**
   * The resource type manipulated by the target endpoint. This is used to determine the RBAC
   * permissions. If not specified, it defaults to ResourceType.SKIP_RBAC, which means no RBAC check
   * will be performed.
   */
  ResourceType resourceType() default ResourceType.SKIP_RBAC;

  /**
   * The resource type for the RBAC check. Default to false. If false, both resourceType and
   * actionPerformed must be specified.
   */
  boolean skipRBAC() default false;
}
