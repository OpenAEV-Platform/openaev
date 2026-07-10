package io.openaev.api.attackpath;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Registers the attack-path POC endpoints only when the {@code ATTACK_PATH_POC} preview feature is
 * enabled (in {@code openaev.enabled-dev-features}, or {@code *}). Mirrors {@code
 * InjectChainingCondition}, the platform's standard way to gate a bean behind a preview feature.
 */
public class AttackPathPocCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
    String features = context.getEnvironment().getProperty("openaev.enabled-dev-features", "");
    return features.equals("*") || features.contains("ATTACK_PATH_POC");
  }
}
