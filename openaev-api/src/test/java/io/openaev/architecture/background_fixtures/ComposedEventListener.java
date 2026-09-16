package io.openaev.architecture.background_fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.event.EventListener;

/**
 * A composed (meta-)annotation over {@code @EventListener}. Spring resolves {@code @EventListener}
 * through {@code AnnotatedElementUtils}, so a method marked with this custom annotation is a live
 * listener a direct-only check would miss. {@code @TransactionalEventListener} is itself
 * meta-annotated with {@code @EventListener}, so the same meta-aware predicate also catches a
 * composed transactional listener. Test-scope only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@EventListener
public @interface ComposedEventListener {}
