package io.openaev.service.period;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.cron.ScheduleFrequency;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
public class CronServiceTest extends IntegrationTest {
  @Autowired private CronService cronService;

  @Nested
  @DisplayName("Compute next occurrence")
  public class ComputeNextOccurrence {
    @Nested
    @DisplayName("With null cron expression")
    public class WithNullCronExpression {
      private final String cronExpression = null;

      @Test
      @DisplayName("Returns empty instant")
      public void returnsEmpty() {
        Optional<Instant> next = cronService.getNextOccurrence(null, Instant.now(), cronExpression);
        assertThat(next).isEmpty();
      }
    }

    @Nested
    @DisplayName("With empty cron expression")
    public class WithEmptyCronExpression {
      private final String cronExpression = "";

      @Test
      @DisplayName("Returns empty instant")
      public void returnsEmpty() {
        Optional<Instant> next = cronService.getNextOccurrence(null, Instant.now(), cronExpression);
        assertThat(next).isEmpty();
      }
    }

    @Nested
    @DisplayName("With exceeding cron limits")
    public class WithExceedingCronLimits {
      private final Instant seedInstant = Instant.parse("2002-04-03T10:43:40Z");

      @Test
      @DisplayName("Throws with hourly cron")
      public void hourlyScheduleIsComputedCorrectly() {
        assertThatThrownBy(
                () -> cronService.getNextOccurrence(null, Instant.now(), "0 0 */1000 * * *"))
            .isInstanceOf(IllegalArgumentException.class);
      }

      @Test
      @DisplayName("Throws with daily cron")
      public void dailyScheduleIsComputedCorrectly() {
        assertThatThrownBy(
                () -> cronService.getNextOccurrence(null, Instant.now(), "0 0 0 * * */1000"))
            .isInstanceOf(IllegalArgumentException.class);
      }

      @Test
      @DisplayName("Throws with weekly cron")
      public void weeklyScheduleIsComputedCorrectly() {
        assertThatThrownBy(
                () -> cronService.getNextOccurrence(null, Instant.now(), "0 0 0 * * 1/7000"))
            .isInstanceOf(IllegalArgumentException.class);
      }

      @Test
      @DisplayName("Throws with monthly cron")
      public void monthlyScheduleIsComputedCorrectly() {
        assertThatThrownBy(
                () -> cronService.getNextOccurrence(null, Instant.now(), "0 0 0 1 */1000 *"))
            .isInstanceOf(IllegalArgumentException.class);
      }
    }

    @Nested
    @DisplayName("With valid cron expression")
    public class WithValidCronExpression {
      String cronExpression = "56 43 10 * * *";

      @Nested
      @DisplayName("With UTC reference instant")
      public class WithUTCReferenceInstant {
        @Test
        @DisplayName("When target time on same day is passed, return expected instant next day")
        public void returnsExpectedInstantNextDay() {
          Instant expected = Instant.parse("2022-04-25T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T14:34:01Z");
          Optional<Instant> next = cronService.getNextOccurrence(null, reference, cronExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same day is not yet passed, return expected instant same day")
        public void returnsExpectedInstantSameDay() {
          Instant expected = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T04:34:01Z");
          Optional<Instant> next = cronService.getNextOccurrence(null, reference, cronExpression);
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
          Optional<Instant> next = cronService.getNextOccurrence(null, reference, cronExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }

        @Test
        @DisplayName(
            "When target time on same day is not yet passed, return expected instant same day")
        public void returnsExpectedInstantSameDay() {
          Instant expected = Instant.parse("2022-04-24T10:43:56Z");
          Instant reference = Instant.parse("2022-04-24T02:34:01+02:00");
          Optional<Instant> next = cronService.getNextOccurrence(null, reference, cronExpression);
          assertThat(next).isPresent().get().isEqualTo(expected);
        }
      }
    }
  }

  @Nested
  @DisplayName("Create cron expression")
  public class createCronExpression {
    private final Instant seedInstant = Instant.parse("2002-04-03T10:43:40Z");

    @Nested
    @DisplayName("With ScheduleFrequency")
    public class WithScheduleFrequency {
      @Test
      @DisplayName("Daily schedule is computed correctly")
      public void dailyScheduleIsComputedCorrectly() {
        ScheduleFrequency sf = ScheduleFrequency.DAILY;

        String expression = cronService.getCronExpression(sf, seedInstant);

        assertThat(expression).isEqualTo("0 43 10 * * *");
      }

      @Test
      @DisplayName("Weekly schedule is computed correctly")
      public void weeklyScheduleIsComputedCorrectly() {
        ScheduleFrequency sf = ScheduleFrequency.WEEKLY;

        String expression = cronService.getCronExpression(sf, seedInstant);

        assertThat(expression).isEqualTo("0 43 10 * * 3");
      }

      @Test
      @DisplayName("Monthly schedule is computed correctly")
      public void monthlyScheduleIsComputedCorrectly() {
        ScheduleFrequency sf = ScheduleFrequency.MONTHLY;

        String expression = cronService.getCronExpression(sf, seedInstant);

        assertThat(expression).isEqualTo("0 43 10 3 * *");
      }

      @Test
      @DisplayName("One-shot schedule is computed correctly")
      public void oneshotScheduleIsComputedCorrectly() {
        ScheduleFrequency sf = ScheduleFrequency.ONESHOT;

        String expression = cronService.getCronExpression(sf, seedInstant);

        assertThat(expression).isNull();
      }
    }

    @Nested
    @DisplayName("With ISO 8601 Period")
    public class WithISO8601Period {
      @Nested
      @DisplayName("With respecting acceptable period bounds")
      public class WithRespectingAcceptablePeriodBounds {
        @Test
        @DisplayName("Hourly schedule is computed correctly")
        public void hourlyScheduleIsComputedCorrectly() {
          String period = "P10H";

          String expression = cronService.getCronExpression(period, seedInstant);

          assertThat(expression).isEqualTo("0 43 */10 * * *");
        }

        @Test
        @DisplayName("Daily schedule is computed correctly")
        public void dailyScheduleIsComputedCorrectly() {
          String period = "P3D";

          String expression = cronService.getCronExpression(period, seedInstant);

          assertThat(expression).isEqualTo("0 43 10 * * */3");
        }

        @Test
        @DisplayName("Weekly schedule is computed correctly")
        public void weeklyScheduleIsComputedCorrectly() {
          String period = "P1W";

          String expression = cronService.getCronExpression(period, seedInstant);

          assertThat(expression).isEqualTo("0 43 10 * * 3/7");
        }

        @Test
        @DisplayName("Monthly schedule is computed correctly")
        public void monthlyScheduleIsComputedCorrectly() {
          String period = "P6M";

          String expression = cronService.getCronExpression(period, seedInstant);

          assertThat(expression).isEqualTo("0 43 10 3 */6 *");
        }
      }

      @Nested
      @DisplayName("With period outside of acceptable bounds")
      public class WithPeriodOutsideOfAcceptableBounds {
        @Test
        @DisplayName("Hourly schedule throws")
        public void hourlyScheduleThrows() {
          String period = "P1000H";

          assertThatThrownBy(() -> cronService.getCronExpression(period, seedInstant))
              .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Daily schedule throws")
        public void dailyScheduleThrows() {
          String period = "P1000D";

          assertThatThrownBy(() -> cronService.getCronExpression(period, seedInstant))
              .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Weekly schedule throws")
        public void weeklyScheduleThrows() {
          String period = "P1000W";

          assertThatThrownBy(() -> cronService.getCronExpression(period, seedInstant))
              .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Monthly schedule throws")
        public void monthlyScheduleThrows() {
          String period = "P1000M";

          assertThatThrownBy(() -> cronService.getCronExpression(period, seedInstant))
              .isInstanceOf(IllegalArgumentException.class);
        }
      }
    }
  }
}
