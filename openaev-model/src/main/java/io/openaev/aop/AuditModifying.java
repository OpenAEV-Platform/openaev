package io.openaev.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Modifying} repository method for entity change auditing. Supports tracking
 * multiple entity types affected by a single query.
 *
 * <p>Usage:
 *
 * <pre>
 * // Single entity type
 * &#64;Modifying
 * &#64;Query("DELETE FROM Inject i WHERE i.id IN :ids")
 * &#64;AuditModifying(@AuditTarget(entityType = Inject.class, paramName = "ids"))
 * void deleteByIds(@Param("ids") Set&lt;String&gt; ids);
 *
 * // Multiple entity types
 * &#64;Modifying
 * &#64;Query("...")
 * &#64;AuditModifying({
 *     &#64;AuditTarget(entityType = Inject.class, paramName = "injectIds"),
 *     &#64;AuditTarget(entityType = InjectExpectation.class, paramName = "expectationIds")
 * })
 * void deleteWithExpectations(@Param("injectIds") Set&lt;String&gt; injectIds,
 *                             @Param("expectationIds") Set&lt;String&gt; expectationIds);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditModifying {

  /** The entity targets to audit. */
  AuditTarget[] value();
}
