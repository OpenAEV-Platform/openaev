package io.openaev.api.chaining.validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.api.chaining.dto.ChainingTimeOutInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TimeOutDurationValidator")
class TimeOutDurationValidatorTest {

  private TimeOutDurationValidator validator;

  @BeforeEach
  void setUp() {
    validator = new TimeOutDurationValidator();
  }

  @Test
  @DisplayName("Should be valid when timeout is disabled regardless of hours and minutes")
  void shouldBeValidWhenTimeOutIsDisabled() {
    ChainingTimeOutInput input =
        ChainingTimeOutInput.builder().isTimeOut(false).timeOutHours(0).timeOutMinutes(0).build();

    assertTrue(validator.isValid(input, null));
  }

  @Test
  @DisplayName("Should be invalid when timeout is enabled and both hours and minutes are zero")
  void shouldBeInvalidWhenTimeOutEnabledAndBothHoursAndMinutesAreZero() {
    ChainingTimeOutInput input =
        ChainingTimeOutInput.builder().isTimeOut(true).timeOutHours(0).timeOutMinutes(0).build();

    assertFalse(validator.isValid(input, null));
  }

  @Test
  @DisplayName("Should be invalid when timeout is enabled and both hours and minutes are null")
  void shouldBeInvalidWhenTimeOutEnabledAndBothHoursAndMinutesAreNull() {
    ChainingTimeOutInput input =
        ChainingTimeOutInput.builder()
            .isTimeOut(true)
            .timeOutHours(null)
            .timeOutMinutes(null)
            .build();

    assertFalse(validator.isValid(input, null));
  }

  @Test
  @DisplayName("Should be valid when timeout is enabled and hours is greater than zero")
  void shouldBeValidWhenTimeOutEnabledAndHoursGreaterThanZero() {
    ChainingTimeOutInput input =
        ChainingTimeOutInput.builder().isTimeOut(true).timeOutHours(1).timeOutMinutes(0).build();

    assertTrue(validator.isValid(input, null));
  }

  @Test
  @DisplayName("Should be valid when timeout is enabled and minutes is greater than zero")
  void shouldBeValidWhenTimeOutEnabledAndMinutesGreaterThanZero() {
    ChainingTimeOutInput input =
        ChainingTimeOutInput.builder().isTimeOut(true).timeOutHours(0).timeOutMinutes(30).build();

    assertTrue(validator.isValid(input, null));
  }

  @Test
  @DisplayName(
      "Should be valid when timeout is enabled and both hours and minutes are greater than zero")
  void shouldBeValidWhenTimeOutEnabledAndBothHoursAndMinutesGreaterThanZero() {
    ChainingTimeOutInput input =
        ChainingTimeOutInput.builder().isTimeOut(true).timeOutHours(1).timeOutMinutes(30).build();

    assertTrue(validator.isValid(input, null));
  }

  @Test
  @DisplayName("Should be valid when input is null")
  void shouldBeValidWhenInputIsNull() {
    assertTrue(validator.isValid(null, null));
  }
}
