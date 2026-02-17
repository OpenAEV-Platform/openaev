package io.openaev.service.period;

import java.time.Instant;
import java.util.Optional;

public interface PeriodExpressionHandler {
  boolean canHandleExpression(String expression);

  Optional<Instant> getNextOccurrence(Instant seed, Instant currentTime, String expression);
}
