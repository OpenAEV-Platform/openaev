package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.FieldUtils.getField;
import static io.openaev.utils.reflection.FieldUtils.setField;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Utility class for working with collection fields via reflection.
 *
 * <p>This class provides methods to identify, instantiate, and manipulate collection-typed fields
 * on entity classes. It handles both {@link Set} and {@link List} types appropriately.
 *
 * @see FieldUtils
 * @see RelationUtils
 */
public class CollectionUtils {

  private CollectionUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks if a field is a collection type.
   *
   * @param field the field to check
   * @return {@code true} if the field is a Collection or Iterable
   * @throws RuntimeException if field is null
   */
  public static boolean isCollection(final Field field) {
    if (field == null) {
      throw new RuntimeException("Field cannot be null");
    }
    return Collection.class.isAssignableFrom(field.getType())
        || Iterable.class.isAssignableFrom(field.getType());
  }

  /**
   * Creates an appropriate empty collection instance for a field.
   *
   * <p>Returns a {@link LinkedHashSet} for Set-typed fields, or an {@link ArrayList} for other
   * collection types.
   *
   * @param field the collection field
   * @return a new empty collection of the appropriate type
   * @throws RuntimeException if field is null
   */
  public static Collection<Object> instantiateCollection(final Field field) {
    if (field == null) {
      throw new RuntimeException("Field cannot be null");
    }
    if (Set.class.isAssignableFrom(field.getType())) {
      return new LinkedHashSet<>();
    }
    return new ArrayList<>();
  }

  /**
   * Replaces the contents of a collection field with new values.
   *
   * <p>If the current field value is null, sets the field to the target collection. Otherwise,
   * clears the existing collection and adds all elements from the target.
   *
   * @param entity the entity containing the collection field
   * @param field the collection field
   * @param target the new collection values
   */
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

  /**
   * Converts a value to a Collection.
   *
   * <p>Handles null, Collection, Iterable, and single values appropriately.
   *
   * @param value the value to convert
   * @return a Collection containing the value(s), or empty list for null
   */
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
