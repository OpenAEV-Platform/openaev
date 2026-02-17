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

  /**
   * Creates a deep copy of a list of entities preserving all properties including IDs.
   *
   * @param <T> the entity type extending Base
   * @param origins the source list of entities to copy
   * @param clazz the entity class
   * @return a new list containing copies of all entities with IDs preserved
   */
  public static <T extends Base> List<T> copy(@NotNull final List<T> origins, Class<T> clazz) {
    List<T> destinations = new ArrayList<>();
    return copyCollection(origins, clazz, destinations, false);
  }

  /**
   * Creates a deep copy of a set of entities preserving all properties including IDs.
   *
   * @param <T> the entity type extending Base
   * @param origins the source set of entities to copy
   * @param clazz the entity class
   * @return a new set containing copies of all entities with IDs preserved
   */
  public static <T extends Base> Set<T> copy(@NotNull final Set<T> origins, Class<T> clazz) {
    Set<T> destinations = new HashSet<>();
    return copyCollection(origins, clazz, destinations, false);
  }

  /**
   * Generic method for copying collections of entities.
   *
   * <p>Copies each entity from the origin collection to the destination collection, optionally
   * excluding ID fields.
   *
   * @param <T> the entity type extending Base
   * @param <C> the collection type
   * @param origins the source collection of entities
   * @param clazz the entity class
   * @param destinations the destination collection to populate
   * @param withoutId if true, excludes ID fields from the copy
   * @return the destination collection populated with copied entities
   * @throws RuntimeException if the copy operation fails
   */
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

  /**
   * Creates a copy of an object excluding fields annotated with {@code @Id}.
   *
   * <p>Uses reflection to copy all fields from the source object to a new instance of the target
   * class, skipping any fields annotated with JPA's {@code @Id} annotation.
   *
   * @param <T> the target type
   * @param <C> the source type
   * @param origin the source object to copy from
   * @param targetClass the target class to instantiate
   * @return a new instance with all non-ID fields copied
   * @throws RuntimeException if the copy operation fails
   */
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
