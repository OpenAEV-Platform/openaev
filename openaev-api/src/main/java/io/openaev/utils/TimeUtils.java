package io.openaev.utils;

import static java.time.ZoneOffset.UTC;

import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimeUtils {

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
}
