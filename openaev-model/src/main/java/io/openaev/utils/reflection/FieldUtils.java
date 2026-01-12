package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.RelationUtils.isRelation;
import static java.lang.reflect.Modifier.isStatic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Utility class for field-level reflection operations.
 *
 * <p>This class provides methods to inspect, read, and modify fields on entity classes using
 * reflection. It handles common patterns like:
 *
 * <ul>
 *   <li>Retrieving all fields including inherited ones
 *   <li>Resolving JSON property names from annotations
 *   <li>Getting and setting field values
 *   <li>Filtering out static, transient, and ignored fields
 * </ul>
 *
 * @see RelationUtils
 * @see CollectionUtils
 */
public class FieldUtils {

  private FieldUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks if a field is static or marked as JPA transient.
   *
   * @param field the field to check
   * @return {@code true} if the field is static or has @Transient annotation
   * @throws IllegalArgumentException if field is null
   */
  public static boolean isStaticOrTransient(final Field field) {
    if (field == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    int m = field.getModifiers();
    return isStatic(m) || field.isAnnotationPresent(jakarta.persistence.Transient.class);
  }

  /**
   * Resolves the JSON property name for a field.
   *
   * <p>If the field has a {@link JsonProperty} annotation, returns its value. Otherwise, returns
   * the Java field name.
   *
   * @param field the field to resolve
   * @return the JSON property name
   * @throws IllegalArgumentException if field is null
   */
  public static String resolveFieldJsonName(final Field field) {
    if (field == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    return field.isAnnotationPresent(JsonProperty.class)
        ? field.getAnnotation(JsonProperty.class).value()
        : field.getName();
  }

  /**
   * Returns all fields from a class and its superclasses.
   *
   * <p>Traverses the class hierarchy up to (but not including) Object to collect all declared
   * fields.
   *
   * @param type the class to inspect
   * @return a list of all fields from the class hierarchy
   * @throws IllegalArgumentException if type is null
   */
  public static List<Field> getAllFields(final Class<?> type) {
    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }
    List<Field> fields = new ArrayList<>();
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      Collections.addAll(fields, c.getDeclaredFields());
    }
    return fields;
  }

  /**
   * Returns all fields with a specific annotation from a class and its superclasses.
   *
   * @param type the class to inspect
   * @param annotationType the annotation to filter by
   * @return a list of fields having the specified annotation
   */
  public static List<Field> getAllDeclaredAnnotatedFields(
      final Class<?> type, final Class<? extends Annotation> annotationType) {
    return getAllFields(type).stream()
        .filter(field -> field.getAnnotation(annotationType) != null)
        .toList();
  }

  /**
   * Returns all non-ignored, non-relational fields as a map keyed by JSON property name.
   *
   * <p>Excludes fields that are: static, transient, have @JsonIgnore, or are JPA relationships.
   *
   * @param clazz the class to inspect
   * @return a map of JSON property name to Field
   */
  public static Map<String, Field> getAllFieldsAsMap(final Class<?> clazz) {
    Map<String, Field> map = new LinkedHashMap<>();
    for (Field f : getAllFields(clazz)) {
      if (f.isAnnotationPresent(JsonIgnore.class) || isRelation(f) || isStaticOrTransient(f)) {
        continue;
      }
      String jsonName = resolveFieldJsonName(f);
      map.put(jsonName, f);
    }
    return map;
  }

  /**
   * Reads the value of a field from an object.
   *
   * <p>Automatically makes the field accessible if needed.
   *
   * @param target the object to read from
   * @param field the field to read
   * @return the field value
   * @throws IllegalArgumentException if target or field is null
   * @throws RuntimeException if the field cannot be accessed
   */
  public static Object getField(final Object target, Field field) {
    if (target == null || field == null) {
      throw new IllegalArgumentException("Target or field cannot be null");
    }
    try {
      if (!field.canAccess(target)) {
        field.setAccessible(true);
      }
      return field.get(target);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot access field " + field.getName(), e);
    }
  }

  /**
   * Computes all field values from an entity as a map.
   *
   * <p>Returns a map of JSON property name to field value, excluding ignored, relational, static,
   * and transient fields.
   *
   * @param entity the entity to read values from
   * @return a map of JSON property names to their values
   */
  public static Map<String, Object> computeAllFieldValues(final Object entity) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Field f : getAllFields(entity.getClass())) {
      if (f.isAnnotationPresent(JsonIgnore.class) || isRelation(f) || isStaticOrTransient(f)) {
        continue;
      }
      String jsonName = resolveFieldJsonName(f);
      Object val = getField(entity, f);
      out.put(jsonName, val);
    }
    return out;
  }

  /**
   * Sets the value of a field on an object.
   *
   * <p>Automatically makes the field accessible if needed.
   *
   * @param target the object to modify
   * @param field the field to set
   * @param value the value to set
   * @throws IllegalArgumentException if target or field is null
   * @throws RuntimeException if the field cannot be set
   */
  public static void setField(Object target, Field field, Object value) {
    if (target == null || field == null) {
      throw new IllegalArgumentException("Target or field cannot be null");
    }
    try {
      if (!field.canAccess(target)) {
        field.setAccessible(true);
      }
      field.set(target, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot set field " + field.getName(), e);
    }
  }
}
