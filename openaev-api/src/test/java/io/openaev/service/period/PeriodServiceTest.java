package io.openaev.service.period;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openaev.IntegrationTest;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
public class PeriodServiceTest extends IntegrationTest {
  @Autowired private PeriodService periodService;

  @Nested
  @DisplayName("Compute next occurrence")
  public class ComputeNextOccurrence {
    @Nested
    @DisplayName("With null period expression")
    public class WithNullPeriodExpression {
      private final String periodExpression = null;

      @Test
      @DisplayName("Returns empty instant")
      public void returnsEmpty() {
        Optional<Instant> next =
            periodService.getNextOccurrence(null, Instant.now(), periodExpression);
        assertThat(next).isEmpty();
      }
    }

    @Nested
    @DisplayName("With empty period expression")
    public class WithEmptyPeriodExpression {
      private final String periodExpression = "";

      @Test
      @DisplayName("Returns empty instant")
      public void returnsEmpty() {
        Optional<Instant> next =
            periodService.getNextOccurrence(null, Instant.now(), periodExpression);
        assertThat(next).isEmpty();
      }
    }

    @Nested
    @DisplayName("With day-based period expression")
    public class WithDayBasedPeriodExpression {
      String periodExpression = "P1D";

      @Nested
      @DisplayName("With UTC reference instant")
      public class WithUTCReferenceInstant {
        @Test
        @DisplayName("When target time on same day is passed, return expected instant next day")
        public void returnsExpectedInstantNextDay() {
          Instant expected = Instant.parse("2022-04-25T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T16:43:56Z");
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same day is not yet passed, return expected instant same day")
        public void returnsExpectedInstantSameDay() {
          Instant expected = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T04:34:01Z");
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }
      }

      @Nested
      @DisplayName("With Zoned reference instant")
      public class WithZonedReferenceInstant {
        @Test
        @DisplayName("When target time on same day is passed, return expected instant next day")
        public void returnsExpectedInstantNextDay() {
          Instant expected = Instant.parse("2022-04-25T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T16:34:01+02:00");
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same day is not yet passed, return expected instant same day")
        public void returnsExpectedInstantSameDay() {
          Instant expected = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T02:34:01+02:00");
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }
      }
    }

    @Nested
    @DisplayName("With hour-based period expression")
    public class WithHourBasedPeriodExpression {
      String periodExpression = "P1H";

      @Nested
      @DisplayName("With UTC reference instant")
      public class WithUTCReferenceInstant {
        @Test
        @DisplayName("When target time on same hour is passed, return expected instant next hour")
        public void returnsExpectedInstantNextHour() {
          Instant expected = Instant.parse("2022-04-24T11:43:56Z");
          Instant reference = Instant.parse("2022-04-24T10:53:56Z");
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same hour is not yet passed, return expected instant same hour")
        public void returnsExpectedInstantSameHour() {
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T10:33:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(seed);
        }
      }

      @Nested
      @DisplayName("With Zoned reference instant")
      public class WithZonedReferenceInstant {
        @Test
        @DisplayName("When target time on same hour is passed, return expected instant next hour")
        public void returnsExpectedInstantNextHour() {
          Instant expected = Instant.parse("2022-04-24T11:43:56Z");
          Instant reference = Instant.parse("2022-04-24T12:53:56+02:00");
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same hour is not yet passed, return expected instant same hour")
        public void returnsExpectedInstantSameHour() {
          Instant expected = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T12:33:56+02:00");
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }
      }
    }

    @Nested
    @DisplayName("With month-based period expression")
    public class WithMonthBasedPeriodExpression {
      String periodExpression = "P1M";

      @Nested
      @DisplayName("With UTC reference instant")
      public class WithUTCReferenceInstant {
        @Test
        @DisplayName("When target time on same month is passed, return expected instant next month")
        public void returnsExpectedInstantNextMonth() {
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Instant expected = LocalDateTime.ofInstant(seed, UTC).plusMonths(1).toInstant(UTC);
          Instant reference = Instant.parse("2022-04-30T10:53:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName("When target time in january is passed, return expected instant next February")
        public void returnsExpectedInstantNextFebruary() {
          Instant seed = Instant.parse("2022-01-30T10:43:56Z");
          // check it respects individual months durations
          Instant expected = Instant.parse("2022-02-28T10:43:56Z");
          Instant reference = Instant.parse("2022-01-31T10:53:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same month is not yet passed, return expected instant same month")
        public void returnsExpectedInstantSameMonth() {
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-23T10:43:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(seed);
        }
      }

      @Nested
      @DisplayName("With Zoned reference instant")
      public class WithZonedReferenceInstant {
        @Test
        @DisplayName("When target time on same month is passed, return expected instant next month")
        public void returnsExpectedInstantNextMonth() {
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Instant expected = LocalDateTime.ofInstant(seed, UTC).plusMonths(1).toInstant(UTC);
          Instant reference = Instant.parse("2022-04-30T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same month is not yet passed, return expected instant same month")
        public void returnsExpectedInstantSameMonth() {
          Instant expected = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-23T12:43:56+02:00");
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }
      }
    }

    @Nested
    @DisplayName("With week-based period expression")
    public class WithWeekBasedPeriodExpression {
      String periodExpression = "P1W";

      @Nested
      @DisplayName("With UTC reference instant")
      public class WithUTCReferenceInstant {
        @Test
        @DisplayName("When target time on same week is passed, return expected instant next week")
        public void returnsExpectedInstantNextWeek() {
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Instant expected = LocalDateTime.ofInstant(seed, UTC).plusWeeks(1).toInstant(UTC);
          Instant reference = Instant.parse("2022-04-26T10:53:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same week is not yet passed, return expected instant same week")
        public void returnsExpectedInstantSameWeek() {
          Instant seed = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-23T10:43:56Z");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(seed);
        }
      }

      @Nested
      @DisplayName("With Zoned reference instant")
      public class WithZonedReferenceInstant {
        @Test
        @DisplayName("When target time on same week is passed, return expected instant next week")
        public void returnsExpectedInstantNextWeek() {
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Instant expected = LocalDateTime.ofInstant(seed, UTC).plusWeeks(1).toInstant(UTC);
          Instant reference = Instant.parse("2022-04-26T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same week is not yet passed, return expected instant same week")
        public void returnsExpectedInstantSameWeek() {
          Instant expected = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-23T12:43:56+02:00");
          Instant seed = Instant.parse("2022-04-24T12:43:56+02:00");
          Optional<Instant> next =
              periodService.getNextOccurrence(seed, reference, periodExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }
      }
    }
  }
}
