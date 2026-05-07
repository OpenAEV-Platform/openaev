package io.openaev.jsonapi;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Options container to control which relationships are included during JSON:API import/export.
 *
 * <p>By default, all relationships are included. However, fields annotated with {@link
 * IncludeOption} can be selectively included or excluded depending on the provided options map.
 *
 * <p>Three modes are supported per relationship:
 *
 * <ul>
 *   <li>{@link IncludeMode#TRUE} — always include (default)
 *   <li>{@link IncludeMode#FALSE} — always exclude
 *   <li>{@link IncludeMode#IF_EXISTS_IN_DB} — include only if the referenced entity already exists
 *       in the database (resolved via {@code @BusinessId}). If not found, the child entity is
 *       skipped instead of being created.
 * </ul>
 */
public record IncludeOptions(Map<String, IncludeMode> modes) {

  /** Controls how a relationship is handled during import/export. */
  public enum IncludeMode {
    /** Always include the relationship. */
    TRUE,
    /** Always exclude the relationship. */
    FALSE,
    /**
     * Include only if the referenced entity already exists in the database. If not found, the
     * entity is skipped rather than created.
     */
    IF_EXISTS_IN_DB
  }

  public static boolean shouldInclude(Field f, IncludeOptions opts) {
    IncludeOption ann = f.getAnnotation(IncludeOption.class);
    if (ann == null) {
      return true;
    }
    String key = ann.key();
    return opts.include(key);
  }

  public boolean include(String relationName) {
    return modes.getOrDefault(relationName, IncludeMode.TRUE) != IncludeMode.FALSE;
  }

  /**
   * Returns {@code true} if the given relationship is configured with {@link
   * IncludeMode#IF_EXISTS_IN_DB}.
   */
  public boolean ifExistsInDb(String relationName) {
    return modes.getOrDefault(relationName, IncludeMode.TRUE) == IncludeMode.IF_EXISTS_IN_DB;
  }

  public static IncludeOptions of(Map<String, IncludeMode> modes) {
    return new IncludeOptions(modes == null ? Map.of() : modes);
  }
}
