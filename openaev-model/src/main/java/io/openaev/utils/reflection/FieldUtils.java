package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.RelationUtils.isRelation;
import static java.lang.reflect.Modifier.isStatic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.util.*;

public class FieldUtils {

  private FieldUtils() {}

  public static boolean isStaticOrTransient(final Field field) {
    if (field == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    int m = field.getModifiers();
    return isStatic(m) || field.isAnnotationPresent(jakarta.persistence.Transient.class);
  }

  // -- GETTER --

  public static String resolveFieldJsonName(final Field field) {
    if (field == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    return field.isAnnotationPresent(JsonProperty.class)
        ? field.getAnnotation(JsonProperty.class).value()
        : field.getName();
  }

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

  // -- SETTER --

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
