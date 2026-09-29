package io.openaev.architecture.background_fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Bean;

/**
 * A composed (meta-)annotation over {@code @Bean}. Spring's configuration parser reads
 * {@code @Bean} through meta-annotation-aware metadata, so a factory method marked with this custom
 * annotation still produces a bean; a factory returning a background type (here {@code
 * CommandLineRunner}) makes its declaring class an entry point, which a direct-only {@code @Bean}
 * check would miss. Test-scope only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Bean
public @interface ComposedBean {}
