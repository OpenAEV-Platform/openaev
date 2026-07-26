package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OperationUtilsRuntime multi-value semantics")
class OperationUtilsRuntimeTest {

  /**
   * Negative operators must combine values with AND (NOT IN semantics): with the historical OR
   * combination, {@code value != A OR value != B} was true for every value as soon as A != B, which
   * made multi-value negative filters match everything.
   */
  @Nested
  @DisplayName("Negative operators combine values with AND (NOT IN)")
  class NegativeOperators {

    @Test
    @DisplayName("notEqualsTexts is false when the value matches one of the texts")
    void notEqualsTexts_should_beFalse_when_valueIsOneOfTheTexts() {
      assertThat(OperationUtilsRuntime.notEqualsTexts("443", List.of("443", "80"))).isFalse();
      assertThat(OperationUtilsRuntime.notEqualsTexts("80", List.of("443", "80"))).isFalse();
    }

    @Test
    @DisplayName("notEqualsTexts is true when the value matches none of the texts")
    void notEqualsTexts_should_beTrue_when_valueMatchesNoText() {
      assertThat(OperationUtilsRuntime.notEqualsTexts("8080", List.of("443", "80"))).isTrue();
    }

    @Test
    @DisplayName("notContainsTexts is false when the value contains one of the texts")
    void notContainsTexts_should_beFalse_when_valueContainsOneText() {
      assertThat(OperationUtilsRuntime.notContainsTexts("hello world", List.of("foo", "world")))
          .isFalse();
    }

    @Test
    @DisplayName("notContainsTexts is true when the value contains none of the texts")
    void notContainsTexts_should_beTrue_when_valueContainsNoText() {
      assertThat(OperationUtilsRuntime.notContainsTexts("hello world", List.of("foo", "bar")))
          .isTrue();
    }

    @Test
    @DisplayName("notStartWithTexts is false when the value starts with one of the texts")
    void notStartWithTexts_should_beFalse_when_valueStartsWithOneText() {
      assertThat(OperationUtilsRuntime.notStartWithTexts("443-tcp", List.of("443", "80")))
          .isFalse();
    }

    @Test
    @DisplayName("notStartWithTexts is true when the value starts with none of the texts")
    void notStartWithTexts_should_beTrue_when_valueStartsWithNoText() {
      assertThat(OperationUtilsRuntime.notStartWithTexts("8443-tcp", List.of("443", "80")))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Positive operators keep OR semantics across values")
  class PositiveOperators {

    @Test
    @DisplayName("equalsTexts is true when the value matches at least one text")
    void equalsTexts_should_beTrue_when_valueMatchesOneText() {
      assertThat(OperationUtilsRuntime.equalsTexts("443", List.of("443", "80"))).isTrue();
      assertThat(OperationUtilsRuntime.equalsTexts("8080", List.of("443", "80"))).isFalse();
    }

    @Test
    @DisplayName("containsTexts is true when the value contains at least one text")
    void containsTexts_should_beTrue_when_valueContainsOneText() {
      assertThat(OperationUtilsRuntime.containsTexts("hello world", List.of("foo", "world")))
          .isTrue();
      assertThat(OperationUtilsRuntime.containsTexts("hello world", List.of("foo", "bar")))
          .isFalse();
    }
  }
}
