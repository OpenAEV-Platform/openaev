package io.openaev.service.catalog;

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/** Release names read {@code YYYY.MM.DD.N}, compared as numbers: {@code N} has up to 9 digits. */
public final class CatalogReleaseOrder {

  private static final Pattern FORMAT =
      Pattern.compile("^\\d{4}\\.\\d{1,2}\\.\\d{1,2}\\.\\d{1,9}$");

  public static final Comparator<String> ORDER = (a, b) -> Arrays.compare(parts(a), parts(b));

  private CatalogReleaseOrder() {}

  public static boolean isValid(String release) {
    return release != null && FORMAT.matcher(release).matches();
  }

  private static long[] parts(String release) {
    return Arrays.stream(release.split("\\.")).mapToLong(Long::parseLong).toArray();
  }
}
