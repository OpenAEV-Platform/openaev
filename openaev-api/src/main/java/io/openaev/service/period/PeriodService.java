package io.openaev.service.period;

import static java.time.ZoneOffset.UTC;

import io.openaev.utils.StringUtils;
import io.openaev.utils.time.TemporalIncrement;
import io.openaev.utils.time.TimeUtils;
import java.time.Instant;
import java.time.LocalDateTime;
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
    if (StringUtils.isBlank(iso8601Period) || seed == null) {
      return Optional.empty();
    }

    // we are forced to convert instants into local date times to increment by weeks or months
    // because why not
    LocalDateTime localOccurrence = LocalDateTime.ofInstant(seed, UTC);
    LocalDateTime localNow = LocalDateTime.ofInstant(now, UTC);

    TemporalIncrement increment = TimeUtils.ISO8601PeriodToTemporalIncrement(iso8601Period);
    while (localOccurrence.isBefore(localNow) || localOccurrence.equals(localNow)) {
      localOccurrence = localOccurrence.plus(increment.quantity(), increment.unit());
    }
    return Optional.of(localOccurrence.toInstant(UTC));
  }
}
