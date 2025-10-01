package io.openaev.utils;

import io.openaev.database.model.Base;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.apache.commons.beanutils.BeanUtils;

public class CopyObjectListUtils {

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

      // Get all declared fields from the source object
      Field[] fields = origin.getClass().getDeclaredFields();

      for (Field field : fields) {
        field.setAccessible(true);

        // Skip the 'id' field
        if (field.isAnnotationPresent(Id.class)) {
          continue;
        }

        // Copy the field value from source to target
        Field targetField = target.getClass().getDeclaredField(field.getName());
        targetField.setAccessible(true);
        targetField.set(target, field.get(origin));
      }
      return target;
    } catch (Exception e) {
      throw new RuntimeException("Failed to copy object", e);
    }
  }
}
