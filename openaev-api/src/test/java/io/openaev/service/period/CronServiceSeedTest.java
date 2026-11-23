package io.openaev.service.period;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CronServiceSeedTest {
  private final CronService cronService = new CronService();

  @Test
  void futureStartIsTheEarliestCronInstant() {
    String cron = "56 43 10 * * *";
    Instant current = Instant.parse("2022-04-24T14:34:01Z");
    Instant start = Instant.parse("2022-04-30T00:00:00Z");

    Optional<Instant> next = cronService.getNextOccurrence(start, current, cron);

    assertThat(next).isPresent();
    assertThat(next.orElseThrow()).isAfterOrEqualTo(start);
    assertThat(next.orElseThrow()).isEqualTo(Instant.parse("2022-04-30T10:43:56Z"));
  }

  @Test
  void pastStartStillUsesTheNextInstantAfterNow() {
    String cron = "56 43 10 * * *";
    Instant current = Instant.parse("2022-04-24T14:34:01Z");
    Instant start = Instant.parse("2022-04-01T00:00:00Z");

    Optional<Instant> next = cronService.getNextOccurrence(start, current, cron);

    assertThat(next).contains(Instant.parse("2022-04-25T10:43:56Z"));
  }
}
