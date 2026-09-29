package io.openaev.architecture.background_fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A composed (meta-)annotation over {@code @Scheduled} (the documented {@code @EveryFiveSeconds}
 * shape). Spring resolves {@code @Scheduled} through {@code AnnotatedElementUtils}, so a method
 * marked with this custom annotation is a live poller a direct-only check would miss. Test-scope
 * only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Scheduled(fixedDelay = 10_000)
public @interface ComposedScheduled {}
