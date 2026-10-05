package io.openaev.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 * The value of the {@code instance_creation_date} setting: an ISO-8601 instant in UTC ({@code
 * 2026-10-05T16:02:00.123Z}), the same whatever the time zone of the JVM that writes or reads it.
 * It bounds a {@code ci} XTM license, so every reader goes through this class.
 *
 * <p>Earlier versions wrote a {@link java.sql.Timestamp} string, a date and time without offset in
 * the time zone of the JVM ({@code 2026-10-05 18:02:00.123}). That form is still read, in the time
 * zone of the reading JVM as before, and rewritten as an instant at startup.
 */
public final class InstanceCreationDate {

  private static final DateTimeFormatter LEGACY_FORMAT =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .optionalStart()
          .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
          .optionalEnd()
          .toFormatter();

  private InstanceCreationDate() {}

  /** The setting value of an instant. */
  public static String format(Instant instant) {
    return instant.toString();
  }

  /** The instant of a setting value, a legacy one read in the JVM time zone. */
  public static Optional<Instant> parse(String value) {
    return parse(value, ZoneId.systemDefault());
  }

  /**
   * The instant of a setting value, or empty when it is blank or unreadable.
   *
   * @param legacyZone the time zone a value in the legacy form was written in
   */
  public static Optional<Instant> parse(String value, ZoneId legacyZone) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String trimmed = value.trim();
    Optional<Instant> instant = parseInstant(trimmed);
    if (instant.isPresent()) {
      return instant;
    }
    try {
      return Optional.of(
          LocalDateTime.parse(trimmed, LEGACY_FORMAT).atZone(legacyZone).toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /** Whether the value is already an instant, which needs no rewriting. */
  public static boolean isInstant(String value) {
    return value != null && parseInstant(value.trim()).isPresent();
  }

  private static Optional<Instant> parseInstant(String value) {
    try {
      return Optional.of(OffsetDateTime.parse(value).toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
