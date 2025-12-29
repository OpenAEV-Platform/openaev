package io.openaev.utilstest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.TimeUtils;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TimeUtilsTest extends IntegrationTest {
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @Nested
  @DisplayName("isISO8601PeriodExpression tests")
  public class IsISO8601PeriodExpressionTests {

    Stream<Arguments> optionsForISO8601Assessment() {
      return Stream.of(
          Arguments.of("P1D", true),
          Arguments.of("", false),
          Arguments.of(null, false),
          Arguments.of("PT10H", true),
          Arguments.of("PT1000H", true),
          Arguments.of("non expression", false),
          Arguments.of("P30U", false),
          Arguments.of("P10W", true),
          Arguments.of("P10M", true));
    }

    @ParameterizedTest
    @MethodSource("optionsForISO8601Assessment")
    @DisplayName("returns expected assessment")
    public void returnsCorrectBool(String expression, boolean expected) {
      assertThat(TimeUtils.isISO8601PeriodExpression(expression)).isEqualTo(expected);
    }
  }

  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @Nested
  @DisplayName("ISO8601PeriodToMilliseconds tests")
  public class ISO8601PeriodToMillisecondsTests {

    Stream<Arguments> optionsForISO8601Assessment() {
      return Stream.of(
          Arguments.of("P1D", 86400000L),
          Arguments.of("PT10H", 36000000L),
          Arguments.of("PT1000H", 3600000000L),
          Arguments.of("P10W", 6048000000L),
          Arguments.of("P10M", 181440000000L));
    }

    @ParameterizedTest
    @MethodSource("optionsForISO8601Assessment")
    @DisplayName("returns expected assessment")
    public void returnsCorrectBool(String expression, long expected) {
      assertThat(TimeUtils.ISO8601PeriodToMilliseconds(expression)).isEqualTo(expected);
    }

    Stream<Arguments> optionsForISO8601AssessmentThrowing() {
      return Stream.of(
          Arguments.of(""),
          Arguments.of((Object) null),
          Arguments.of("non expression"),
          Arguments.of("P30U"));
    }

    @ParameterizedTest
    @MethodSource("optionsForISO8601AssessmentThrowing")
    @DisplayName("throws as expected")
    public void throwsAsExpected(String expression) {
      assertThatThrownBy(() -> TimeUtils.ISO8601PeriodToMilliseconds(expression))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
