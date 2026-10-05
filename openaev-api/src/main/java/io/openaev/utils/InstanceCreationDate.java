package io.openaev.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 * The value of the {@code instance_creation_date} setting. It is written as an ISO-8601 instant in
 * UTC ({@code 2026-10-05T16:02:00.123Z}), the same whatever the time zone of the JVM that writes or
 * reads it. It bounds a {@code ci} XTM license, so every reader goes through this class.
 *
 * <p>Two forms are read. An ISO-8601 date and time with an offset ({@code Z} or {@code +02:00})
 * names exactly one instant; without an offset it is unreadable. Earlier versions wrote a {@link
 * java.sql.Timestamp} string, a date and time without offset in the time zone of the JVM ({@code
 * 2026-10-05 18:02:00.123}); that form is still read, in the time zone of the reading JVM as
 * before. At startup, a readable value that is not in the canonical form {@link #format} writes is
 * rewritten in it, as the same instant.
 */
public final class InstanceCreationDate {

  /** The forms {@link #parse} reads, for an operator correcting an unreadable value. */
  public static final String READABLE_FORMS =
      "an ISO-8601 date and time with an offset, such as 2026-10-05T16:02:00.123Z, or the legacy"
          + " form 2026-10-05 18:02:00.123 in the time zone of this JVM";

  // Strict: an impossible date or a '.' without digits is unreadable, never adjusted
  private static final DateTimeFormatter LEGACY_FORMAT =
      new DateTimeFormatterBuilder()
          .appendPattern("uuuu-MM-dd HH:mm:ss")
          .optionalStart()
          .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
          .optionalEnd()
          .toFormatter()
          .withResolverStyle(ResolverStyle.STRICT);

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
      // The later offset in a daylight-saving overlap, as java.sql.Timestamp.valueOf resolves it
      return Optional.of(
          LocalDateTime.parse(trimmed, LEGACY_FORMAT)
              .atZone(legacyZone)
              .withLaterOffsetAtOverlap()
              .toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /**
   * Whether the value is exactly what {@link #format} writes for its instant, which needs no
   * rewriting. Another offset than {@code Z}, other fraction digits or surrounding blanks are not.
   */
  public static boolean isCanonical(String value) {
    return value != null
        && parseInstant(value).map(InstanceCreationDate::format).filter(value::equals).isPresent();
  }

  private static Optional<Instant> parseInstant(String value) {
    try {
      return Optional.of(OffsetDateTime.parse(value).toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
