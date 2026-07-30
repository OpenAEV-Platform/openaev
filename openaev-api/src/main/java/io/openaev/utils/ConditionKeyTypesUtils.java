package io.openaev.utils;

import io.openaev.database.model.ConditionType;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;
import java.util.List;
import java.util.Objects;

/** Normalizes condition key types with safe defaults and event-specific constraints. */
public final class ConditionKeyTypesUtils {

  private ConditionKeyTypesUtils() {}

  /** Removes nulls and duplicates. Returns an empty list when the input is null or empty. */
  public static List<PrimitiveType> normalize(List<PrimitiveType> keyTypes) {
    if (keyTypes == null) return List.of();
    return keyTypes.stream().filter(Objects::nonNull).distinct().toList();
  }

  /**
   * Applies condition-type and mapping-type business rules:
   *
   * <ul>
   *   <li>DEFAULT mapper → {@code null} (no state lookup, value is static)
   *   <li>MAPPER (GLOBAL/LOCAL) → cleaned list, defaults to {@link PrimitiveType#Text} if empty
   *   <li>non-MAPPER → single key, defaults to {@link PrimitiveType#Text} if empty
   * </ul>
   */
  public static List<PrimitiveType> normalizeForConditionType(
      List<PrimitiveType> keyTypes, ConditionType conditionType, MappingType mappingType) {
    if (conditionType == ConditionType.MAPPER && mappingType == MappingType.DEFAULT) {
      return null;
    }
    List<PrimitiveType> normalized = normalize(keyTypes);
    if (normalized.isEmpty()) {
      normalized = List.of(PrimitiveType.Text);
    }
    if (conditionType != ConditionType.MAPPER && normalized.size() > 1) {
      return List.of(normalized.getFirst());
    }
    return normalized;
  }
}
