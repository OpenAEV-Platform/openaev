package io.openaev.jsonapi;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Options container to control which relationships are included during JSON:API export.
 *
 * <p>By default, all relationships are included. However, fields annotated with {@link
 * IncludeOption} can be selectively included or excluded depending on the provided options map.
 */
public record IncludeOptions(Map<String, Boolean> includes) {

  public static boolean shouldInclude(Field f, IncludeOptions opts) {
    IncludeOption ann = f.getAnnotation(IncludeOption.class);
    if (ann == null) {
      return true;
    }
    String key = ann.key();
    return opts.include(key);
  }

  public boolean include(String relationName) {
    return includes.getOrDefault(relationName, true);
  }

  public static IncludeOptions of(Map<String, Boolean> f) {
    return new IncludeOptions(f == null ? Map.of() : f);
  }
}
