package io.openaev.service.scenario;

import io.openaev.database.model.Scenario;
import io.openaev.service.period.RecurrenceService;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@RequiredArgsConstructor
@Slf4j
@Service
public class ScenarioRecurrenceService {
  private final RecurrenceService recurrenceService;

  public Optional<Instant> getNextExecutionTime(@NotNull Scenario scenario, Instant currentTime) {
    Optional<Instant> nextOccurrence =
        recurrenceService.getNextOccurrence(
            scenario.getRecurrence(), scenario.getRecurrenceStart(), currentTime);
    if (nextOccurrence.isEmpty()) {
      log.warn(
          "Attempted to compute a next occurrence for scenario {} but could not find a period expression handler for recurrence expression '{}'",
          scenario.getId(),
          scenario.getRecurrence());
    }
    return nextOccurrence;
  }
}
