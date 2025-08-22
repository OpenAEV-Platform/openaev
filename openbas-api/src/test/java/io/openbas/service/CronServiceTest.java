package io.openbas.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openbas.IntegrationTest;
import io.openbas.service.utils.CronService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
public class CronServiceTest extends IntegrationTest {
  @Autowired private CronService cronService;

  private final ZoneId UTC = ZoneId.of("UTC");

  @Nested
  @DisplayName("With null cron expression")
  public class WithNullCronExpression {
    private final String cronExpression = null;

    @Test
    @DisplayName("Returns empty instant")
    public void returnsEmpty() {
      Optional<Instant> next =
          cronService.getNextExecutionFromInstant(Instant.now(), UTC, cronExpression);
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
      Optional<Instant> next =
          cronService.getNextExecutionFromInstant(Instant.now(), UTC, cronExpression);
      assertThat(next).isEmpty();
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
        Optional<Instant> next =
            cronService.getNextExecutionFromInstant(reference, UTC, cronExpression);
        assertThat(next).isPresent().get().isEqualTo(expected);
      }

      @Test
      @DisplayName(
          "When target time on same day is not yet passed, return expected instant same day")
      public void returnsExpectedInstantSameDay() {
        Instant expected = Instant.parse("2022-04-24T10:43:56Z");
        Instant reference = Instant.parse("2022-04-24T04:34:01Z");
        Optional<Instant> next =
            cronService.getNextExecutionFromInstant(reference, UTC, cronExpression);
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
        Optional<Instant> next =
            cronService.getNextExecutionFromInstant(reference, UTC, cronExpression);
        assertThat(next).isPresent().get().isEqualTo(expected);
      }

      @Test
      @DisplayName(
          "When target time on same day is not yet passed, return expected instant same day")
      public void returnsExpectedInstantSameDay() {
        Instant expected = Instant.parse("2022-04-24T10:43:56Z");
        Instant reference = Instant.parse("2022-04-24T02:34:01+02:00");
        Optional<Instant> next =
            cronService.getNextExecutionFromInstant(reference, UTC, cronExpression);
        assertThat(next).isPresent().get().isEqualTo(expected);
      }
    }
  }
}
