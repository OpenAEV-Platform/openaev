package io.openaev.service.period;

import io.openaev.utils.StringUtils;
import io.openaev.utils.TimeUtils;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PeriodService implements PeriodExpressionHandler {
  @Override
  public boolean canHandleExpression(String expression) {
    return TimeUtils.isISO8601PeriodExpression(expression);
  }

  @Override
  public Optional<Instant> getNextOccurrence(Instant seed, Instant now, String iso8601Period) {
    if (StringUtils.isBlank(iso8601Period)) {
      return Optional.empty();
    }

    Instant occurrence = seed;
    long millis = TimeUtils.ISO8601PeriodToMilliseconds(iso8601Period);
    while (occurrence.isBefore(now) || occurrence.equals(now)) {
      occurrence = occurrence.plusMillis(millis);
    }
    return Optional.of(occurrence);
  }
}
