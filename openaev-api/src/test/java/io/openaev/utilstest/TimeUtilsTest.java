package io.openaev.utilstest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.time.TemporalIncrement;
import io.openaev.utils.time.TimeUtils;
import java.time.temporal.ChronoUnit;
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
  @DisplayName("ISO8601PeriodToTemporalIncrement tests")
  public class ISO8601PeriodToTemporalIncrementTests {

    Stream<Arguments> optionsForISO8601Assessment() {
      return Stream.of(
          Arguments.of("P1D", new TemporalIncrement(1, ChronoUnit.DAYS)),
          Arguments.of("PT10H", new TemporalIncrement(10, ChronoUnit.HOURS)),
          Arguments.of("PT1000H", new TemporalIncrement(1000, ChronoUnit.HOURS)),
          Arguments.of("P10W", new TemporalIncrement(10, ChronoUnit.WEEKS)),
          Arguments.of("P10M", new TemporalIncrement(10, ChronoUnit.MONTHS)));
    }

    @ParameterizedTest
    @MethodSource("optionsForISO8601Assessment")
    @DisplayName("returns expected assessment")
    public void returnsCorrectBool(String expression, TemporalIncrement expected) {
      assertThat(TimeUtils.ISO8601PeriodToTemporalIncrement(expression)).isEqualTo(expected);
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
      assertThatThrownBy(() -> TimeUtils.ISO8601PeriodToTemporalIncrement(expression))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
