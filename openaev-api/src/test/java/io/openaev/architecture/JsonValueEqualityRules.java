package io.openaev.architecture;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Walks the declared type of a JSON-mapped field and lists every application type it reaches that
 * has no value equality.
 *
 * <p>Hibernate decides whether a loaded entity changed by comparing each JSON value with a copy it
 * made by round-tripping it through JSON. Whatever the mapping, that comparison ends up calling
 * {@code equals} on the stored objects as soon as one of them is a collection, a map, or declares
 * its own {@code equals}. A type that inherits {@code Object.equals} then compares by identity,
 * never equal to its copy, so the row is rewritten on every flush.
 *
 * <p>What the walk accepts as having value equality:
 *
 * <ul>
 *   <li>primitives, enums, and the JDK value types a JSON round trip produces ({@code String},
 *       numbers, {@code Boolean}, {@code java.time}, {@code UUID});
 *   <li>Jackson's {@link JsonNode};
 *   <li>{@code Object}: after a load Jackson only ever produces maps, lists and scalars for it;
 *   <li>an application type that declares {@code equals} and {@code hashCode} below {@code Object},
 *       whose own fields pass the same walk.
 * </ul>
 *
 * <p>What it cannot see: whether a declared {@code equals} is correct (one that skips a field, or
 * Lombok's {@code callSuper = false} on a type whose parent holds state, passes); a runtime subtype
 * Jackson can produce that is not listed in {@link JsonSubTypes}. An interface without that list
 * and a JDK type outside the allowlist are reported rather than guessed.
 */
final class JsonValueEqualityRules {

  private static final Set<Class<?>> JDK_VALUE_TYPES =
      Set.of(
          String.class,
          Boolean.class,
          Character.class,
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          java.math.BigDecimal.class,
          java.math.BigInteger.class,
          java.util.UUID.class,
          Object.class);

  private JsonValueEqualityRules() {}

  /**
   * The application types without value equality reached from {@code fields}, each mapped to the
   * fields it was reached from, as {@code Class.field} keys.
   */
  static Map<String, Set<String>> typesWithoutValueEquality(Map<String, Field> fields) {
    Map<String, Set<String>> violations = new TreeMap<>();
    fields.forEach(
        (key, field) ->
            walk(field.getGenericType(), new HashSet<>())
                .forEach(
                    type -> violations.computeIfAbsent(type, unused -> new TreeSet<>()).add(key)));
    return violations;
  }

  /** The types without value equality reachable from {@code type}. */
  static Set<String> walk(Type type, Set<Class<?>> visited) {
    Set<String> violations = new TreeSet<>();
    if (type instanceof ParameterizedType parameterized) {
      violations.addAll(walk(parameterized.getRawType(), visited));
      for (Type argument : parameterized.getActualTypeArguments()) {
        violations.addAll(walk(argument, visited));
      }
    } else if (type instanceof GenericArrayType array) {
      violations.addAll(walk(array.getGenericComponentType(), visited));
    } else if (type instanceof WildcardType wildcard) {
      for (Type bound : wildcard.getUpperBounds()) {
        violations.addAll(walk(bound, visited));
      }
    } else if (type instanceof TypeVariable<?> variable) {
      for (Type bound : variable.getBounds()) {
        violations.addAll(walk(bound, visited));
      }
    } else if (type instanceof Class<?> raw) {
      violations.addAll(walkClass(raw, visited));
    }
    return violations;
  }

  private static Set<String> walkClass(Class<?> raw, Set<Class<?>> visited) {
    Set<String> violations = new TreeSet<>();
    if (raw.isArray()) {
      return walk(raw.getComponentType(), visited);
    }
    if (raw.isPrimitive()
        || raw.isEnum()
        || JDK_VALUE_TYPES.contains(raw)
        || raw.getName().startsWith("java.time.")
        || JsonNode.class.isAssignableFrom(raw)
        || Collection.class.isAssignableFrom(raw)
        || Map.class.isAssignableFrom(raw)) {
      // A collection or a map compares element by element; its elements are walked through the
      // type arguments of the declaration that reached it.
      return violations;
    }
    if (raw.getName().startsWith("java.") || raw.getName().startsWith("javax.")) {
      violations.add(raw.getName() + " (JDK type outside the allowlist)");
      return violations;
    }
    if (!visited.add(raw)) {
      return violations;
    }
    JsonSubTypes subTypes = raw.getAnnotation(JsonSubTypes.class);
    if (subTypes != null) {
      for (JsonSubTypes.Type subType : subTypes.value()) {
        violations.addAll(walk(subType.value(), visited));
      }
    }
    if (subTypes != null && (raw.isInterface() || Modifier.isAbstract(raw.getModifiers()))) {
      // What is stored is one of the listed subtypes, walked above.
      return violations;
    }
    if (raw.isInterface()) {
      violations.add(raw.getName() + " (interface without listed subtypes)");
      return violations;
    }
    if (!hasValueEquality(raw, false)) {
      violations.add(raw.getName());
    }
    // Each class of the hierarchy that holds state declares its own equality, so a subclass equals
    // cannot leave a parent's fields out of the comparison by inheriting Object's.
    for (Class<?> type = raw; type != null && type != Object.class; type = type.getSuperclass()) {
      List<Field> state = stateFields(type);
      if (type != raw && !state.isEmpty() && !hasValueEquality(type, true)) {
        violations.add(type.getName());
      }
      for (Field field : state) {
        violations.addAll(walk(field.getGenericType(), visited));
      }
    }
    return violations;
  }

  /** The fields that take part in the JSON value, declared on {@code type} itself. */
  private static List<Field> stateFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Field field : type.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())
          && !Modifier.isTransient(field.getModifiers())
          && !field.isAnnotationPresent(JsonIgnore.class)
          && !field.isSynthetic()) {
        fields.add(field);
      }
    }
    return fields;
  }

  /**
   * Whether {@code type} has {@code equals} and {@code hashCode} other than {@code Object}'s:
   * declared on the type itself when {@code declaredHere}, anywhere below {@code Object} otherwise.
   */
  private static boolean hasValueEquality(Class<?> type, boolean declaredHere) {
    try {
      Method equals = type.getMethod("equals", Object.class);
      Method hashCode = type.getMethod("hashCode");
      return declaredHere
          ? equals.getDeclaringClass() == type && hashCode.getDeclaringClass() == type
          : equals.getDeclaringClass() != Object.class
              && hashCode.getDeclaringClass() != Object.class;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }
}
