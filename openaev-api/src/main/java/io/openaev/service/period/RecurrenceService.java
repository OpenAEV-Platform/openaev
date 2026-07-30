package io.openaev.service.period;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Entity-agnostic recurrence evaluation: resolves the {@link PeriodExpressionHandler} able to
 * interpret a recurrence expression (cron or ISO 8601 period) and computes the next occurrence.
 * Shared by scenario and atomic testing scheduling jobs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurrenceService {

  private final List<PeriodExpressionHandler> periodExpressionHandlers;

  /**
   * Computes the next occurrence of a recurrence expression.
   *
   * @param expression the recurrence expression (cron or ISO 8601 period)
   * @param seed the recurrence start date (used by period-based handlers)
   * @param currentTime the reference time to compute the next occurrence from
   * @return the next occurrence, or empty if no handler can interpret the expression
   */
  public Optional<Instant> getNextOccurrence(String expression, Instant seed, Instant currentTime) {
    // Short-circuit before probing handlers: a null/blank expression can never match and probing
    // would only produce parse-exception noise from the cron handler.
    if (expression == null || expression.isBlank()) {
      return Optional.empty();
    }

    Optional<PeriodExpressionHandler> handler =
        periodExpressionHandlers.stream()
            .filter(h -> h.canHandleExpression(expression))
            .findFirst();

    if (handler.isEmpty()) {
      log.warn(
          "Could not find a period expression handler for recurrence expression '{}'", expression);
      return Optional.empty();
    }
    return handler.get().getNextOccurrence(seed, currentTime, expression);
  }
}
