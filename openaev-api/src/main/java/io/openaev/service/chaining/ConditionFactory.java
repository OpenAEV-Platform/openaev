package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;

public class ConditionFactory {

  private static Condition build(
      String key,
      ConditionType type,
      PrimitiveType keyType,
      String value,
      MappingType mappingType) {
    return Condition.builder()
        .key(key)
        .type(type)
        .keyType(keyType)
        .value(value)
        .mappingType(mappingType)
        .build();
  }

  public static Condition executionOf(Condition source, Object goal) {
    if (source == null || source.getType() == null) {
      throw new IllegalArgumentException("Source conditions must not be null and must have a type");
    }

    return build(
        source.getKey(),
        source.getType(),
        null,
        goal != null ? goal.toString() : null,
        source.getMappingType());
  }

  public static Condition dependOn(String stepTemplateId) {
    if (stepTemplateId == null || stepTemplateId.isBlank()) {
      throw new IllegalArgumentException("stepTemplateId must not be null or blank");
    }
    return build(stepTemplateId, ConditionType.DEPEND_ON, null, stepTemplateId, null);
  }
}
