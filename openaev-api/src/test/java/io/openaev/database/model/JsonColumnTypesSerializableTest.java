package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Entity;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Hypersistence snapshots every {@code @Type(JsonType.class)} attribute with Java serialization
 * (its default {@code ObjectMapperJsonSerializer}), so whatever a JSON column can hold must be
 * {@link Serializable}, down to the last referenced class. A missing one only fails when an entity
 * holding it is loaded ({@code NonSerializableObjectException}); this test fails at build time
 * instead.
 *
 * <p>Walks, from each JSON attribute of each {@code @Entity}: field types and their generic
 * arguments, the fields of every reached class and its superclasses, and the subtypes declared with
 * {@code @JsonSubTypes}. JDK container interfaces ({@code Map}, {@code List}…) and {@code Object}
 * are not checked themselves (the runtime instances are JDK collections), but their type arguments
 * are. {@link JsonNode} is skipped: Hypersistence copies it with {@code deepCopy()}.
 */
@DisplayName("Types stored in JSON columns are Serializable")
class JsonColumnTypesSerializableTest {

  private static final String BASE_PACKAGE = "io.openaev";

  @Test
  void given_jsonMappedAttributes_should_onlyReachSerializableTypes() throws Exception {
    // Arrange
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    Set<String> notSerializable = new TreeSet<>();
    Set<Class<?>> visited = new HashSet<>();
    int jsonAttributes = 0;

    // Act
    for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
      for (Class<?> c = Class.forName(candidate.getBeanClassName());
          c != null && c != Object.class;
          c = c.getSuperclass()) {
        for (Field field : c.getDeclaredFields()) {
          org.hibernate.annotations.Type type =
              field.getAnnotation(org.hibernate.annotations.Type.class);
          if (type != null && type.value() == JsonType.class) {
            jsonAttributes++;
            String origin = c.getSimpleName() + "." + field.getName();
            walk(field.getGenericType(), origin, visited, notSerializable);
          }
        }
      }
    }

    // Assert
    assertThat(jsonAttributes).as("JSON attributes found").isPositive();
    assertThat(notSerializable)
        .as("classes reachable from a @Type(JsonType.class) attribute must implement Serializable")
        .isEmpty();
  }

  private static void walk(
      Type type, String origin, Set<Class<?>> visited, Set<String> notSerializable) {
    switch (type) {
      case ParameterizedType parameterized -> {
        walk(parameterized.getRawType(), origin, visited, notSerializable);
        for (Type argument : parameterized.getActualTypeArguments()) {
          walk(argument, origin, visited, notSerializable);
        }
      }
      case WildcardType wildcard -> {
        for (Type bound : wildcard.getUpperBounds()) {
          walk(bound, origin, visited, notSerializable);
        }
      }
      case TypeVariable<?> variable -> {
        for (Type bound : variable.getBounds()) {
          walk(bound, origin, visited, notSerializable);
        }
      }
      case GenericArrayType array ->
          walk(array.getGenericComponentType(), origin, visited, notSerializable);
      case Class<?> c -> walkClass(c, origin, visited, notSerializable);
      default -> {}
    }
  }

  private static void walkClass(
      Class<?> c, String origin, Set<Class<?>> visited, Set<String> notSerializable) {
    if (c.isArray()) {
      walkClass(c.getComponentType(), origin, visited, notSerializable);
      return;
    }
    if (c.isPrimitive()
        || c == Object.class
        || JsonNode.class.isAssignableFrom(c)
        || (c.isInterface() && c.getName().startsWith("java."))
        || !visited.add(c)) {
      return;
    }
    if (!Serializable.class.isAssignableFrom(c)) {
      notSerializable.add(c.getName() + " (reached from " + origin + ")");
    }
    if (!c.getName().startsWith(BASE_PACKAGE)) {
      return;
    }
    for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
      for (Field field : k.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())
            && !Modifier.isTransient(field.getModifiers())) {
          walk(field.getGenericType(), origin, visited, notSerializable);
        }
      }
    }
    JsonSubTypes subTypes = c.getAnnotation(JsonSubTypes.class);
    if (subTypes != null) {
      for (JsonSubTypes.Type subType : subTypes.value()) {
        walkClass(subType.value(), origin, visited, notSerializable);
      }
    }
  }
}
