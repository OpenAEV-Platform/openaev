package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * An entity's {@code equals}/{@code hashCode} must not read its mapped {@code Set}s or {@code
 * Map}s. On a loaded entity they are a {@code PersistentSet} / {@code PersistentMap}, whose {@code
 * hashCode}/{@code equals} initialize the collection: hashing the owner then loads all of it
 * (#8443, an endpoint targeted by many simulations loaded every inject each time it was put in a
 * {@code HashSet}, up to an OOM). A mapped {@code List} ({@code PersistentBag}) is hashed by
 * identity and never loaded, so it is not checked. Typically caused by Lombok's field-based
 * {@code @Data} / {@code @EqualsAndHashCode}: base identity on the id instead.
 *
 * <p>Each mapped {@code Set}/{@code Map} field is, in turn, replaced by a collection that fails as
 * soon as it is hashed, compared or iterated, then {@code hashCode} and {@code equals} are called
 * on two instances sharing the same id.
 */
@DisplayName("Entity equals/hashCode never load mapped Sets or Maps")
class EntityIdentityIgnoresCollectionsTest {

  private static final String BASE_PACKAGE = "io.openaev";
  private static final String SHARED_ID = "identity-test-id";

  @Test
  void given_entities_should_notReadMappedSetsOrMaps_inEqualsOrHashCode() throws Exception {
    // Arrange
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    Set<String> violations = new TreeSet<>();
    int checked = 0;

    for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
      Class<?> entity = Class.forName(candidate.getBeanClassName());
      for (Field field : mappedSetsAndMaps(entity)) {
        Object first = instantiate(entity);
        Object second = instantiate(entity);
        if (first == null || second == null) {
          break;
        }
        setId(first);
        setId(second);
        field.set(first, tripwire(entity, field));
        field.set(second, tripwire(entity, field));
        checked++;

        // Act
        try {
          first.hashCode();
          first.equals(second);
          second.equals(first);
        } catch (CollectionRead read) {
          violations.add(read.getMessage());
        } catch (RuntimeException e) {
          throw new IllegalStateException("equals/hashCode of " + entity.getName() + " failed", e);
        }
      }
    }

    // Assert
    assertThat(checked).as("mapped Set/Map fields checked").isPositive();
    assertThat(violations)
        .as("equals/hashCode must use the id, not the mapped Sets/Maps (see class javadoc)")
        .isEmpty();
  }

  private static Object instantiate(Class<?> entity) {
    if (Modifier.isAbstract(entity.getModifiers())) {
      return null;
    }
    try {
      Constructor<?> constructor = entity.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static List<Field> mappedSetsAndMaps(Class<?> entity) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        boolean mapped =
            field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(ManyToMany.class)
                || field.isAnnotationPresent(ElementCollection.class);
        Class<?> type = field.getType();
        if (!mapped
            || Modifier.isStatic(field.getModifiers())
            || !(Set.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))) {
          continue;
        }
        // Tripwires extend HashSet / HashMap: a field typed with another concrete class (e.g.
        // TreeSet) cannot receive one and would silently go unchecked.
        if (!type.isAssignableFrom(TripwireSet.class)
            && !type.isAssignableFrom(TripwireMap.class)) {
          throw new IllegalStateException(
              "cannot check " + entity.getSimpleName() + "." + field.getName() + ": " + type);
        }
        field.setAccessible(true);
        fields.add(field);
      }
    }
    return fields;
  }

  private static void setId(Object instance) throws IllegalAccessException {
    for (Class<?> c = instance.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
      try {
        Field id = c.getDeclaredField("id");
        Object value = sharedId(id.getType());
        if (value != null) {
          id.setAccessible(true);
          id.set(instance, value);
        }
        return;
      } catch (NoSuchFieldException ignored) {
        // declared higher in the hierarchy
      }
    }
  }

  private static Object sharedId(Class<?> type) {
    if (type == String.class) {
      return SHARED_ID;
    }
    if (type == UUID.class) {
      return UUID.nameUUIDFromBytes(SHARED_ID.getBytes(StandardCharsets.UTF_8));
    }
    if (type == Long.class || type == long.class) {
      return 1L;
    }
    return null;
  }

  private static Object tripwire(Class<?> entity, Field field) {
    String name = entity.getSimpleName() + "." + field.getName();
    return Map.class.isAssignableFrom(field.getType())
        ? new TripwireMap<>(name)
        : new TripwireSet<>(name);
  }

  private static final class CollectionRead extends RuntimeException {
    CollectionRead(String field) {
      super(field, null, false, false);
    }
  }

  private static final class TripwireSet<E> extends HashSet<E> {
    private final String field;

    TripwireSet(String field) {
      this.field = field;
    }

    @Override
    public int hashCode() {
      throw new CollectionRead(field);
    }

    @Override
    public boolean equals(Object o) {
      throw new CollectionRead(field);
    }

    @Override
    public Iterator<E> iterator() {
      throw new CollectionRead(field);
    }
  }

  private static final class TripwireMap<K, V> extends HashMap<K, V> {
    private final String field;

    TripwireMap(String field) {
      this.field = field;
    }

    @Override
    public int hashCode() {
      throw new CollectionRead(field);
    }

    @Override
    public boolean equals(Object o) {
      throw new CollectionRead(field);
    }
  }
}
