package io.openaev.utils;

import io.openaev.database.model.PrimitiveType;
import java.util.Map;

public final class PrimitiveValueMaskingUtils {

  private record MaskRule(int prefixLength, int suffixLength) {}

  private static final Map<PrimitiveType, MaskRule> MASK_RULES =
      Map.of(
          PrimitiveType.Password, new MaskRule(1, 1),
          PrimitiveType.Hash, new MaskRule(3, 3),
          PrimitiveType.Key, new MaskRule(3, 3));

  private PrimitiveValueMaskingUtils() {}

  public static String maskValue(PrimitiveType type, String value) {
    if (value == null || type == null) {
      return value;
    }
    MaskRule rule = MASK_RULES.get(type);
    if (rule == null) {
      return value;
    }
    if (value.length() <= rule.prefixLength() + rule.suffixLength()) {
      return "*".repeat(value.length());
    }
    int maskedLength = value.length() - rule.prefixLength() - rule.suffixLength();
    return value.substring(0, rule.prefixLength())
        + "*".repeat(maskedLength)
        + value.substring(value.length() - rule.suffixLength());
  }

  public static boolean isMaskedEcho(PrimitiveType type, String rawValue, String candidateValue) {
    if (type == null || rawValue == null || candidateValue == null) {
      return false;
    }
    return maskValue(type, rawValue).equals(candidateValue);
  }
}
