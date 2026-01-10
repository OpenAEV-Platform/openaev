package io.openaev.utils;

import io.openaev.database.model.Base;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.apache.commons.beanutils.BeanUtils;

public class CopyObjectListUtils {

  private CopyObjectListUtils() {}

  public static <T extends Base> List<T> copyWithoutIds(
      @NotNull final List<T> origins, Class<T> clazz) {
    List<T> destinations = new ArrayList<>();
    return copyCollection(origins, clazz, destinations, true);
  }

  public static <T extends Base> List<T> copy(@NotNull final List<T> origins, Class<T> clazz) {
    List<T> destinations = new ArrayList<>();
    return copyCollection(origins, clazz, destinations, false);
  }

  public static <T extends Base> Set<T> copy(@NotNull final Set<T> origins, Class<T> clazz) {
    Set<T> destinations = new HashSet<>();
    return copyCollection(origins, clazz, destinations, false);
  }

  public static <T extends Base, C extends Collection<T>> C copyCollection(
      @NotNull final C origins, Class<T> clazz, C destinations, Boolean withoutId) {
    origins.forEach(
        origin -> {
          try {
            if (withoutId) {
              destinations.add(copyObjectWithoutId(origin, clazz));
            } else {
              T destination = clazz.getDeclaredConstructor().newInstance();
              BeanUtils.copyProperties(destination, origin);
              destinations.add(destination);
            }
          } catch (IllegalAccessException
              | InvocationTargetException
              | InstantiationException
              | NoSuchMethodException e) {
            throw new RuntimeException("Failed to copy object", e);
          }
        });
    return destinations;
  }

  public static <T, C> T copyObjectWithoutId(C origin, Class<T> targetClass) {
    try {
      T target = targetClass.getDeclaredConstructor().newInstance();

      // Get all declared fields from the source object including inherited fields
      List<Field> allFields = getAllFields(origin.getClass());

      for (Field field : allFields) {
        field.setAccessible(true);

        // Skip the 'id' field
        if (field.isAnnotationPresent(Id.class)) {
          continue;
        }

        // Copy the field value from source to target
        try {
          Field targetField = getField(target.getClass(), field.getName());
          if (targetField != null) {
            targetField.setAccessible(true);
            targetField.set(target, field.get(origin));
          }
        } catch (NoSuchFieldException ignored) {
          // Field doesn't exist in target class, skip it
        }
      }
      return target;
    } catch (Exception e) {
      throw new RuntimeException("Failed to copy object", e);
    }
  }

  /**
   * Get all fields from a class including inherited fields from superclasses.
   *
   * @param clazz the class to get fields from
   * @return a list of all fields including inherited ones
   */
  private static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    while (clazz != null && clazz != Object.class) {
      fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
      clazz = clazz.getSuperclass();
    }
    return fields;
  }

  /**
   * Get a field from a class including inherited fields from superclasses.
   *
   * @param clazz the class to search in
   * @param fieldName the name of the field to find
   * @return the field if found
   * @throws NoSuchFieldException if the field is not found in the class hierarchy
   */
  private static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    while (clazz != null && clazz != Object.class) {
      try {
        return clazz.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
