package io.openaev.annotation.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Suppresses the compile-time {@link EndpointTransactionalRule} check for a specific REST endpoint
 * method.
 *
 * <p>Use this when an endpoint intentionally omits {@code @Transactional} — for example, when
 * adding a transaction boundary causes deadlocks or test hangs due to entity-graph cascades.
 *
 * <p>Always document the reason in a comment next to the annotation.
 *
 * <pre>{@code
 * @SuppressTransactionalCheck // Deadlock: native DELETE + ON DELETE CASCADE, see #44f7c443
 * @DeleteMapping("/api/exercises/{exerciseId}")
 * public void deleteExercise(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface SuppressTransactionalCheck {}
