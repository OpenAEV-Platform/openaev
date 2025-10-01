package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.FieldUtils.getField;
import static io.openaev.utils.reflection.FieldUtils.setField;

import java.lang.reflect.Field;
import java.util.*;

public class CollectionUtils {

  private CollectionUtils() {}

  public static boolean isCollection(final Field field) {
    if (field == null) {
      throw new RuntimeException("Field cannot be null");
    }
    return Collection.class.isAssignableFrom(field.getType())
        || Iterable.class.isAssignableFrom(field.getType());
  }

  // -- SETTER --

  public static Collection<Object> instantiateCollection(final Field field) {
    if (field == null) {
      throw new RuntimeException("Field cannot be null");
    }
    if (Set.class.isAssignableFrom(field.getType())) {
      return new LinkedHashSet<>();
    }
    return new ArrayList<>();
  }

  @SuppressWarnings("unchecked")
  public static void replaceCollection(Object entity, Field field, Collection<Object> target) {
    Collection<Object> current = (Collection<Object>) getField(entity, field);
    if (current == null) {
      setField(entity, field, target);
    } else {
      current.clear();
      current.addAll(target);
    }
  }

  @SuppressWarnings("unchecked")
  public static Collection<?> toCollection(Object value) {
    switch (value) {
      case null -> {
        return List.of();
      }
      case Collection<?> c -> {
        return c;
      }
      case Iterable<?> it -> {
        List<Object> out = new ArrayList<>();
        it.forEach(out::add);
        return out;
      }
      default -> {}
    }
    return List.of(value);
  }
}
