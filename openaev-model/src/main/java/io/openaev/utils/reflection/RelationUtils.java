package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.FieldUtils.*;

import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for working with JPA relationship annotations via reflection.
 *
 * <p>This class provides methods to identify and manipulate JPA relationship fields ({@link
 * OneToMany}, {@link ManyToOne}, {@link OneToOne}, {@link ManyToMany}) on entity classes.
 *
 * @see FieldUtils
 * @see CollectionUtils
 */
public class RelationUtils {

  private RelationUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks if a field is a JPA relationship.
   *
   * @param f the field to check
   * @return {@code true} if the field has any JPA relationship annotation
   */
  public static boolean isRelation(final Field f) {
    return f.isAnnotationPresent(OneToMany.class)
        || f.isAnnotationPresent(ManyToOne.class)
        || f.isAnnotationPresent(OneToOne.class)
        || f.isAnnotationPresent(ManyToMany.class);
  }

  /**
   * Returns all relationship fields of a class as a map keyed by JSON property name.
   *
   * @param clazz the class to inspect
   * @return a map of JSON property name to Field for all relationship fields
   */
  public static Map<String, Field> getAllRelationsAsMap(final Class<?> clazz) {
    Map<String, Field> map = new LinkedHashMap<>();
    for (Field f : getAllFields(clazz)) {
      if (!isRelation(f)) {
        continue;
      }
      String relName = resolveFieldJsonName(f);
      map.put(relName, f);
    }
    return map;
  }

  /**
   * Sets the inverse side of a bidirectional relationship.
   *
   * <p>Finds the first relationship field in the child entity that matches the parent's type and
   * sets it to the parent object. This is useful for maintaining bidirectional relationships when
   * adding child entities.
   *
   * @param child the child entity
   * @param parent the parent entity to set on the child
   * @throws IllegalArgumentException if child or parent is null
   */
  public static void setInverseRelation(Object child, Object parent) {
    if (child == null || parent == null) {
      throw new IllegalArgumentException("Child or parent cannot be null");
    }
    Map<String, Field> childRelations = getAllRelationsAsMap(child.getClass());
    for (Field childField : childRelations.values()) {
      if (childField.getType().isAssignableFrom(parent.getClass())) {
        setField(child, childField, parent);
        break;
      }
    }
  }
}
