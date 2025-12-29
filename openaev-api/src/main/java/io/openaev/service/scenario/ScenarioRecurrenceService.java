package io.openaev.service.scenario;

import io.openaev.database.model.Scenario;
import io.openaev.service.period.PeriodExpressionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@RequiredArgsConstructor
@Service
public class ScenarioRecurrenceService {
  private final List<PeriodExpressionHandler> periodExpressionHandlers;

  public Optional<Instant> getNextExecutionTime(@NotNull Scenario scenario, Instant currentTime) {
    if (!isScenarioRecurrent(scenario, currentTime)) {
      return Optional.empty();
    }
    PeriodExpressionHandler handler =
        periodExpressionHandlers.stream()
            .filter(h -> h.canHandleExpression(scenario.getRecurrence()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No interpreter found for value of scenario recurrence expression: %s"
                            .formatted(scenario.getRecurrence())));
    return handler.getNextOccurrence(
        scenario.getRecurrenceStart(), currentTime, scenario.getRecurrence());
  }

  private boolean isScenarioRecurrent(@NotNull Scenario scenario, Instant currentTime) {
    return (scenario.getRecurrenceStart() == null
            || scenario.getRecurrenceStart().isBefore(currentTime))
        && scenario.getRecurrence() != null
        && (scenario.getRecurrenceEnd() == null
            || scenario.getRecurrenceEnd().isAfter(currentTime));
  }
}
