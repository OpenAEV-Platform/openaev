package io.openaev.engine;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class EsUtils {

  /** Millisecond-exact ISO-8601 in UTC, the fraction always written. */
  private static final DateTimeFormatter ENGINE_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private EsUtils() {}

  /**
   * Formats an instant as a search-engine date bound, at millisecond precision with the fraction
   * always written. {@link Instant#toString()} drops a zero fraction ({@code 02:40:01Z}), and
   * Elasticsearch and OpenSearch round the missing components of a {@code gt} or {@code lte} bound
   * UP to the end of that second: {@code lte 02:40:01Z} would admit {@code 02:40:01.999Z} and
   * {@code gt 02:40:01Z} would skip everything up to it. Sub-millisecond digits are dropped, which
   * matches the millisecond resolution of a {@code date} field.
   *
   * @param instant the bound (must not be null)
   * @return the bound as {@code uuuu-MM-dd'T'HH:mm:ss.SSS'Z'}
   */
  public static String toEngineDate(Instant instant) {
    return ENGINE_DATE.format(instant);
  }

  public static List<String> buildRestrictions(String... ids) {
    List<String> restrictions = new ArrayList<>();
    List<String> filters = Arrays.stream(ids).filter(Objects::nonNull).toList();
    if (!filters.isEmpty()) {
      restrictions.addAll(filters);
    }
    if (restrictions.isEmpty()) {
      return null;
    }
    return restrictions;
  }
}
