package io.openaev.utils;

import io.openaev.database.model.Marked;
import java.util.Arrays;
import java.util.Set;

public final class MarkingUtils {

  private MarkingUtils() {}

  public static Set<String> markingIds(Marked marked) {
    String[] markingIds = marked.getMarkingIds();
    return markingIds == null ? Set.of() : Set.copyOf(Arrays.asList(markingIds));
  }
}
