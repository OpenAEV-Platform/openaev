package io.openaev.service.catalog;

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Release names read {@code MAJOR.YYMMDD.N}, or {@code YYYY.MM.DD.N} for the first releases (major
 * 0), compared as numbers.
 */
public final class CatalogReleaseOrder {

  private static final Pattern RELEASE =
      Pattern.compile("^([1-9]\\d{0,2})\\.(\\d{2})(\\d{2})(\\d{2})\\.(0|[1-9]\\d{0,8})$");
  private static final Pattern FIRST_RELEASE =
      Pattern.compile("^(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})\\.(\\d{1,9})$");

  public static final Comparator<String> ORDER = (a, b) -> Arrays.compare(parts(a), parts(b));

  private CatalogReleaseOrder() {}

  public static boolean isValid(String release) {
    return parts(release) != null;
  }

  private static long[] parts(String release) {
    if (release == null) {
      return null;
    }
    Matcher first = FIRST_RELEASE.matcher(release);
    if (first.matches()) {
      return new long[] {0, num(first, 1), num(first, 2), num(first, 3), num(first, 4)};
    }
    Matcher next = RELEASE.matcher(release);
    if (next.matches()) {
      return new long[] {
        num(next, 1), 2000 + num(next, 2), num(next, 3), num(next, 4), num(next, 5)
      };
    }
    return null;
  }

  private static long num(Matcher matcher, int group) {
    return Long.parseLong(matcher.group(group));
  }
}
