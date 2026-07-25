package io.openaev.utils;

import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import java.util.List;
import java.util.Objects;

/** Normalizes condition key types with safe defaults and event-specific constraints. */
public final class ConditionKeyTypesUtils {

  private ConditionKeyTypesUtils() {}

  /**
   * Ensures key types are never null/empty and contain unique non-null values.
   *
   * <p>Defaults to {@link PrimitiveType#Text} when missing.
   */
  public static List<PrimitiveType> normalize(List<PrimitiveType> keyTypes) {
    List<PrimitiveType> normalized =
        keyTypes == null
            ? List.of()
            : keyTypes.stream().filter(Objects::nonNull).distinct().toList();
    return normalized.isEmpty() ? List.of(PrimitiveType.Text) : normalized;
  }

  /**
   * Enforces the single-key invariant for non-mapper conditions while preserving mapper multi-keys.
   */
  public static List<PrimitiveType> normalizeForConditionType(
      List<PrimitiveType> keyTypes, ConditionType conditionType) {
    List<PrimitiveType> normalized = normalize(keyTypes);
    if (conditionType != ConditionType.MAPPER && normalized.size() > 1) {
      return List.of(normalized.getFirst());
    }
    return normalized;
  }
}
