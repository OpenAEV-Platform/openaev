package io.openaev.service.period;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openaev.IntegrationTest;
import jakarta.transaction.Transactional;
import java.time.Instant;
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
    public class WithNullCronExpression {
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
    public class WithEmptyCronExpression {
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
    @DisplayName("With valid period expression")
    public class WithValidPeriodExpression {
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
  }
}
