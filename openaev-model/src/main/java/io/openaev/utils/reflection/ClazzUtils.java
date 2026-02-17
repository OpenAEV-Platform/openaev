package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.FieldUtils.getAllFields;
import static io.openaev.utils.reflection.FieldUtils.getField;

import io.openaev.database.model.Base;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import java.lang.reflect.Field;

/**
 * Utility class for class-level reflection operations.
 *
 * <p>This class provides methods for instantiating entity classes and reading entity identifiers
 * via reflection.
 *
 * @see FieldUtils
 */
public class ClazzUtils {

  private ClazzUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Creates a new instance of a {@link Base} entity class using its no-arg constructor.
   *
   * @param clazz the entity class to instantiate
   * @param <T> the entity type
   * @return a new instance of the class
   * @throws IllegalArgumentException if clazz is null
   * @throws RuntimeException if instantiation fails (e.g., no accessible no-arg constructor)
   */
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

  /**
   * Reads the ID value from an entity using reflection.
   *
   * <p>Scans all fields for {@link Id} or {@link EmbeddedId} annotations and returns the value as a
   * String.
   *
   * @param entity the entity to read the ID from
   * @return the ID as a String, or {@code null} if no ID field is found or the value is null
   * @throws IllegalArgumentException if entity is null
   */
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
