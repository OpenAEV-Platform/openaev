package io.openaev.architecture.background_fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.scheduling.annotation.Async;

/**
 * A composed (meta-)annotation over {@code @Async}. Spring resolves {@code @Async} through {@code
 * AnnotatedElementUtils}, so a method marked with this custom annotation runs on a pool thread
 * exactly as a direct {@code @Async} would, off the caller's transaction and tenant scope. A
 * direct-only detector sees neither {@code @Async} here nor any other marker, so this is the
 * near-miss the meta-annotation-aware predicate must catch. Test-scope only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Async
public @interface ComposedAsync {}
