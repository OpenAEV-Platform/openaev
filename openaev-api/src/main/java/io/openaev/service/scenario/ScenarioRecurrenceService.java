package io.openaev.service.scenario;

import io.openaev.database.model.Scenario;
import io.openaev.service.period.PeriodExpressionHandler;
import java.time.Instant;
import java.util.List;
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
  private final List<PeriodExpressionHandler> periodExpressionHandlers;

  public Optional<Instant> getNextExecutionTime(@NotNull Scenario scenario, Instant currentTime) {
    Optional<PeriodExpressionHandler> handler =
        periodExpressionHandlers.stream()
            .filter(h -> h.canHandleExpression(scenario.getRecurrence()))
            .findFirst();

    if (handler.isEmpty()) {
      log.warn(
          "Attempted to compute a next occurrence for scenario {} but could not find a period expression handler for recurrence expression '{}'",
          scenario.getId(),
          scenario.getRecurrence());
      return Optional.empty();
    }
    return handler
        .get()
        .getNextOccurrence(scenario.getRecurrenceStart(), currentTime, scenario.getRecurrence());
  }
}
