package io.openaev.utils.reflection;

import static io.openaev.utils.reflection.FieldUtils.*;

import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

public class RelationUtils {

  private RelationUtils() {}

  public static boolean isRelation(final Field f) {
    return f.isAnnotationPresent(OneToMany.class)
        || f.isAnnotationPresent(ManyToOne.class)
        || f.isAnnotationPresent(OneToOne.class)
        || f.isAnnotationPresent(ManyToMany.class);
  }

  // -- GETTER --

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

  // -- SETTER --

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
