package io.openaev.service.period;

import io.openaev.rest.exception.BadRequestException;
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

  /** Accepted forms, as named to API callers when an expression is refused. */
  public static final String ACCEPTED_RECURRENCE_FORMS =
      "use a Spring 6-field cron, seconds first, evaluated in UTC, e.g. '0 0 2 * * *', or an"
          + " ISO 8601 period such as P1D";

  private final List<PeriodExpressionHandler> periodExpressionHandlers;

  /**
   * Whether one of the handlers the scheduling jobs use can interpret the expression. A null or
   * blank expression is not supported (callers normalise it to "no recurrence" first).
   *
   * @param expression the recurrence expression
   * @return true when a scheduler handler can parse it
   */
  public boolean isSupportedExpression(String expression) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    return periodExpressionHandlers.stream().anyMatch(h -> h.canHandleExpression(expression));
  }

  /**
   * Refuses a recurrence expression the scheduler could never fire, so it is not stored silently. A
   * null or blank expression is accepted: it clears the schedule.
   *
   * @param expression the normalised recurrence expression, or null to clear
   * @throws BadRequestException when no handler can interpret a non-blank expression
   */
  public void validateExpression(String expression) {
    if (expression == null || expression.isBlank()) {
      return;
    }
    if (!isSupportedExpression(expression)) {
      throw new BadRequestException(
          "Unsupported recurrence expression '%s': %s"
              .formatted(expression, ACCEPTED_RECURRENCE_FORMS));
    }
  }

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
