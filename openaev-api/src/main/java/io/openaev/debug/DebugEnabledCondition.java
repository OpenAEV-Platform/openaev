package io.openaev.debug;

import java.util.Arrays;
import java.util.Set;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Production barrier: debug mode runs only when {@code enabled=true} AND (a non-production profile
 * is active OR {@code allow-in-production=true}). Production = no {@code dev}/{@code test}/{@code
 * ci} profile (there is no explicit {@code production} profile).
 */
public class DebugEnabledCondition implements Condition {

  static final Set<String> NON_PRODUCTION_PROFILES = Set.of("dev", "test", "ci");

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Environment env = context.getEnvironment();
    if (!env.getProperty("openaev.debug.enabled", Boolean.class, false)) {
      return false;
    }
    if (env.getProperty("openaev.debug.allow-in-production", Boolean.class, false)) {
      return true;
    }
    return !isProduction(env);
  }

  static boolean isProduction(Environment env) {
    return Arrays.stream(env.getActiveProfiles()).noneMatch(NON_PRODUCTION_PROFILES::contains);
  }
}
