package io.openaev.utils;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.PrimitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PrimitiveValueMaskingUtils")
class PrimitiveValueMaskingUtilsTest {

  @Test
  @DisplayName("should mask password/hash/key with expected visible edges")
  void shouldMaskSensitiveValues() {
    assertEquals("S******3", PrimitiveValueMaskingUtils.maskValue(PrimitiveType.Password, "Secret13"));
    assertEquals("ABC***XYZ", PrimitiveValueMaskingUtils.maskValue(PrimitiveType.Hash, "ABC123XYZ"));
    assertEquals("KEY******890", PrimitiveValueMaskingUtils.maskValue(PrimitiveType.Key, "KEY123456890"));
  }

  @Test
  @DisplayName("should keep non-sensitive values unchanged")
  void shouldKeepNonSensitiveValues() {
    assertEquals("plain", PrimitiveValueMaskingUtils.maskValue(PrimitiveType.Text, "plain"));
  }

  @Test
  @DisplayName("should detect masked echoes")
  void shouldDetectMaskedEchoes() {
    assertTrue(PrimitiveValueMaskingUtils.isMaskedEcho(PrimitiveType.Password, "TopSecret", "T*******t"));
    assertFalse(PrimitiveValueMaskingUtils.isMaskedEcho(PrimitiveType.Password, "TopSecret", "TopSecret"));
  }
}
