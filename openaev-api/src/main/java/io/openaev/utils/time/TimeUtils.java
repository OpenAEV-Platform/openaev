package io.openaev.utils.time;

import static java.time.ZoneOffset.UTC;

import io.openaev.cron.ScheduleFrequency;
import io.openaev.utils.StringUtils;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {
  private static final String ISO_8601_PERIOD_EXPRESSION_MASK =
      "PT?(?<digits>\\d+)(?<magnitude>[HDWM])";

  public static Instant toInstant(@NotNull final String dateString) {
    String pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault());
    LocalDateTime localDateTime = LocalDateTime.parse(dateString, dateTimeFormatter);
    ZonedDateTime zonedDateTime = localDateTime.atZone(UTC);
    return zonedDateTime.toInstant();
  }

  public static Instant toInstantFlexible(String dateString) {
    if (dateString == null || dateString.isBlank()) {
      return null;
    }

    if (dateString.length() == 10) {
      LocalDate d = LocalDate.parse(dateString);
      return d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    return Instant.parse(dateString);
  }

  public static TemporalIncrement ISO8601PeriodToTemporalIncrement(
      @NotNull final String iso8601PeriodExpression) {
    Matcher matcher = matchPattern(iso8601PeriodExpression, ISO_8601_PERIOD_EXPRESSION_MASK);
    if (matcher.find()) {
      int amount = Integer.parseInt(matcher.group("digits"));
      return switch (ScheduleFrequency.fromString(matcher.group("magnitude"))) {
        case HOURLY -> new TemporalIncrement(amount, ChronoUnit.HOURS);
        case DAILY -> new TemporalIncrement(amount, ChronoUnit.DAYS);
        case WEEKLY -> new TemporalIncrement(amount, ChronoUnit.WEEKS);
        case MONTHLY -> new TemporalIncrement(amount, ChronoUnit.MONTHS);
        default -> throw new IllegalArgumentException("Unrecognised period interval unit");
      };
    }
    throw new IllegalArgumentException(
        "Could not parse argument as ISO 8601 Period expression; argument: %s"
            .formatted(iso8601PeriodExpression));
  }

  public static Instant incrementInstant(Instant reference, TemporalIncrement increment) {
    LocalDateTime ldt = LocalDateTime.ofInstant(reference, UTC);
    ldt = ldt.plus(increment.quantity(), increment.unit());
    return ldt.toInstant(UTC);
  }

  public static boolean isISO8601PeriodExpression(String expression) {
    return matchPattern(expression, ISO_8601_PERIOD_EXPRESSION_MASK).find();
  }

  private static Matcher matchPattern(String expression, String mask) {
    return Pattern.compile(mask).matcher(StringUtils.isBlank(expression) ? "" : expression);
  }
}
