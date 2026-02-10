package io.openaev.utilstest;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.StringUtils;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("String Utils tests")
class StringUtilsTest {

  @Test
  @DisplayName("isValidUUID should accept valid UUID")
  void testIsValidUUID_withValidUUID() {
    String validUUID = UUID.randomUUID().toString();
    assertDoesNotThrow(() -> StringUtils.isValidUUID(validUUID));
  }

  @Test
  @DisplayName("isValidUUID should throw BadRequestException for invalid UUID")
  void testIsValidUUID_withInvalidUUID() {
    String invalidUUID = "not-a-valid-uuid";
    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> StringUtils.isValidUUID(invalidUUID));
    assertEquals(
        "Invalid import ID format, It couldn't be parsed as UUID.", exception.getMessage());
  }

  @Test
  @DisplayName("isValidUUID should throw BadRequestException for empty string")
  void testIsValidUUID_withEmptyString() {
    assertThrows(BadRequestException.class, () -> StringUtils.isValidUUID(""));
  }

  @Test
  @DisplayName("isValidUUID should throw BadRequestException for null")
  void testIsValidUUID_withNull() {
    assertThrows(BadRequestException.class, () -> StringUtils.isValidUUID(null));
  }
}
