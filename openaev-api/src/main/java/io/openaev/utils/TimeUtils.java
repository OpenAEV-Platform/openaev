package io.openaev.utils;

import static java.time.ZoneOffset.UTC;

import io.openaev.cron.ScheduleFrequency;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

  public static long ISO8601PeriodToMilliseconds(@NotNull final String iso8601PeriodExpression) {
    long second = 1000L;
    Matcher matcher = matchPattern(iso8601PeriodExpression, ISO_8601_PERIOD_EXPRESSION_MASK);
    if (matcher.find()) {
      long singleUnitMillis =
          switch (ScheduleFrequency.fromString(matcher.group("magnitude"))) {
            case HOURLY -> 3600 * second;
            case DAILY -> 24 * 3600 * second;
            case WEEKLY -> 7 * 24 * 3600 * second;
            case MONTHLY -> 30 * 24 * 3600 * second;
            default -> throw new IllegalArgumentException("Unrecognised period interval unit");
          };
      return singleUnitMillis * Integer.parseInt(matcher.group("digits"));
    }
    throw new IllegalArgumentException(
        "Could not parse argument as ISO 8601 Period expression; argument: %s"
            .formatted(iso8601PeriodExpression));
  }

  public static boolean isISO8601PeriodExpression(String expression) {
    return matchPattern(expression, ISO_8601_PERIOD_EXPRESSION_MASK).find();
  }

  private static Matcher matchPattern(String expression, String mask) {
    return Pattern.compile(mask).matcher(StringUtils.isBlank(expression) ? "" : expression);
  }
}
