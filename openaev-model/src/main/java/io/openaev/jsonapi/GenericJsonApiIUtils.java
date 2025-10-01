package io.openaev.jsonapi;

import static org.springframework.util.StringUtils.hasText;

import jakarta.persistence.Table;

public class GenericJsonApiIUtils {

  public static final String CAMEL_CASE_REGEX = "([a-z])([A-Z])";

  private GenericJsonApiIUtils() {}

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

  private static String toSnakeCase(String s) {
    return s.replaceAll(CAMEL_CASE_REGEX, "$1_$2").replace('-', '_').toLowerCase();
  }
}
