package io.openaev.service;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class InjectChainingCondition implements Condition {
  @Override
  public boolean matches(ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
    String features = context.getEnvironment().getProperty("openaev.enabled-dev-features", "");
    return features.equals("*") || features.contains("INJECT_CHAINING");
  }
}
