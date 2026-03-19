package io.openaev.jsonapi;

import static org.springframework.util.StringUtils.hasText;

import jakarta.persistence.Table;

/**
 * Utility class for JSON API type resolution and string conversions.
 *
 * <p>This class provides helper methods for working with JSON API type identifiers, which are used
 * to identify resource types in JSON API documents.
 */
public class GenericJsonApiIUtils {

  /** Regex pattern for identifying camelCase word boundaries. */
  public static final String CAMEL_CASE_REGEX = "([a-z])([A-Z])";

  private GenericJsonApiIUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Resolves the JSON API type identifier for a given entity class.
   *
   * <p>The type is determined in the following order:
   *
   * <ol>
   *   <li>If the class has a {@link Table} annotation with a non-empty name, use that name
   *   <li>Otherwise, convert the class simple name to snake_case
   * </ol>
   *
   * @param clazz the entity class to resolve the type for
   * @return the JSON API type identifier (e.g., "exercises", "inject_expectations")
   * @throws IllegalArgumentException if clazz is null
   */
  public static String resolveType(final Class<?> clazz) {
    if (clazz == null) {
      throw new IllegalArgumentException("Clazz cannot be null");
    }
    Table annotation = clazz.getAnnotation(Table.class);
    if (annotation != null) {
      String tableName = annotation.name();
      if (hasText(tableName)) {
        return tableName.trim();
      }
    }
    return toSnakeCase(clazz.getSimpleName());
  }

  /**
   * Converts a string from camelCase to snake_case.
   *
   * @param s the string to convert
   * @return the snake_case version of the string
   */
  private static String toSnakeCase(String s) {
    return s.replaceAll(CAMEL_CASE_REGEX, "$1_$2").replace('-', '_').toLowerCase();
  }
}
