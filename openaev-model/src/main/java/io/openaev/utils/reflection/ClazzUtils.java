package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.FieldUtils.getAllFields;
import static io.openaev.utils.reflection.FieldUtils.getField;

import io.openaev.database.model.Base;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import java.lang.reflect.Field;

public class ClazzUtils {

  private ClazzUtils() {}

  public static <T extends Base> T instantiate(final Class<T> clazz) {
    if (clazz == null) {
      throw new IllegalArgumentException("Cannot instantiate null class");
    }
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Cannot instantiate " + clazz.getName(), e);
    }
  }

  // -- ID --

  public static String readId(Object entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity cannot be null");
    }
    for (Field f : getAllFields(entity.getClass())) {
      if (f.isAnnotationPresent(Id.class) || f.isAnnotationPresent(EmbeddedId.class)) {
        Object v = getField(entity, f);
        return v != null ? String.valueOf(v) : null;
      }
    }
    return null;
  }
}
