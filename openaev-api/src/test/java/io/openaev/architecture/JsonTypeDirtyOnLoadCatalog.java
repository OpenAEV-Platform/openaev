package io.openaev.architecture;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * The fields the dirty-on-load family can live in, and the table each of them belongs to.
 *
 * <p>A field is a candidate when its column holds JSON, whichever of the two mappings this codebase
 * uses: hypersistence {@link JsonType}, or Hibernate's own {@code @JdbcTypeCode(SqlTypes.JSON)}.
 * Both dirty-check by comparing the loaded value with a snapshot they build by serializing and
 * deserializing it, and when the two cannot be seen as equal the entity is dirty after every load,
 * so a read flushes an {@code UPDATE}. Both have produced the defect here: {@code
 * AutonomousScopeTarget} documents it on the native mapping and {@code Payload} on the
 * hypersistence one, so a rule written for one of them would have missed the other.
 *
 * <p>Fields are keyed by their DECLARING class, not by the entity that inherits them. A
 * single-table hierarchy ({@code Payload} and its payload types, {@code Asset} and its asset types)
 * would otherwise report the same field once per subclass and read as if each subclass needed its
 * own measurement.
 */
final class JsonTypeDirtyOnLoadCatalog {

  /** Where the production activation list lives, relative to the module root. */
  private static final String APPLICATION_PROPERTIES = "src/main/resources/application.properties";

  private JsonTypeDirtyOnLoadCatalog() {}

  /** A {@link JsonType}-mapped field, identified by the class that declares it. */
  record JsonField(Class<?> declaringClass, String fieldName) implements Comparable<JsonField> {

    String key() {
      return declaringClass.getSimpleName() + "." + fieldName;
    }

    @Override
    public int compareTo(JsonField other) {
      return key().compareTo(other.key());
    }

    @Override
    public String toString() {
      return key();
    }
  }

  /**
   * Every {@link JsonType}-mapped field reachable from a mapped entity, with the tables it is
   * stored in. A field declared on a base class shared by entities of several tables maps to all of
   * them.
   */
  static Map<JsonField, Set<String>> jsonFieldsByTable() {
    Map<JsonField, Set<String>> tables = new TreeMap<>();
    for (Class<?> entity : mappedEntities()) {
      String table = tableOf(entity);
      for (JsonField field : jsonFields(entity)) {
        tables.computeIfAbsent(field, unused -> new TreeSet<>()).add(table);
      }
    }
    return tables;
  }

  /** The {@link JsonType}-mapped fields of one entity, including the ones it inherits. */
  static List<JsonField> jsonFields(Class<?> entity) {
    List<JsonField> fields = new ArrayList<>();
    for (Class<?> type = entity;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        if (isJsonTypeMapped(field)) {
          fields.add(new JsonField(type, field.getName()));
        }
      }
    }
    return fields;
  }

  /** The values of one instance's {@link JsonType}-mapped fields, keyed as {@link JsonField}. */
  static Map<JsonField, Object> jsonFieldValues(Object entity) {
    Map<JsonField, Object> values = new LinkedHashMap<>();
    for (JsonField field : jsonFields(entity.getClass())) {
      try {
        Field raw = field.declaringClass().getDeclaredField(field.fieldName());
        raw.setAccessible(true);
        values.put(field, raw.get(entity));
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("cannot read " + field, e);
      }
    }
    return values;
  }

  private static boolean isJsonTypeMapped(Field field) {
    Type hypersistence = field.getAnnotation(Type.class);
    if (hypersistence != null && hypersistence.value() == JsonType.class) {
      return true;
    }
    JdbcTypeCode hibernateNative = field.getAnnotation(JdbcTypeCode.class);
    return hibernateNative != null && hibernateNative.value() == SqlTypes.JSON;
  }

  /**
   * The table an entity's rows live in, walking up the hierarchy because {@code @Table} is not
   * inherited and a single-table subclass shares its parent's mapping. Mirrors what {@code
   * TenantTables} does on the same question.
   */
  static String tableOf(Class<?> entity) {
    for (Class<?> type = entity;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      Table table = type.getAnnotation(Table.class);
      if (table != null && !table.name().isBlank()) {
        return table.name().toLowerCase(Locale.ROOT);
      }
    }
    throw new IllegalStateException("mapped entity without @Table(name=...): " + entity.getName());
  }

  /**
   * Every {@code @Entity} class on the classpath, abstract ones included: an abstract entity
   * carries the mapping its subclasses store, so skipping it (which the default component scan
   * does) would leave its fields out of the inventory.
   */
  static Set<Class<?>> mappedEntities() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return true;
          }
        };
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    Set<Class<?>> entities = new LinkedHashSet<>();
    for (BeanDefinition definition : scanner.findCandidateComponents("io.openaev")) {
      String className = definition.getBeanClassName();
      try {
        entities.add(Class.forName(className));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("scanned entity cannot be loaded: " + className, e);
      }
    }
    return entities;
  }

  /**
   * The tenant tables activated in production, read from {@code application.properties} rather than
   * from the environment: the test classpath declares no activation list, so a running context
   * would report none active and every assertion built on it would be vacuous.
   */
  static Set<String> activeTables() {
    Properties properties = new Properties();
    try (InputStream in = new FileInputStream(APPLICATION_PROPERTIES)) {
      properties.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + APPLICATION_PROPERTIES, e);
    }
    return Arrays.stream(properties.getProperty("openaev.tenant.active-tables", "").split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** The candidate fields stored in at least one tenant-active table. */
  static Set<JsonField> fieldsOnActiveTables() {
    Set<String> active = activeTables();
    return jsonFieldsByTable().entrySet().stream()
        .filter(entry -> entry.getValue().stream().anyMatch(active::contains))
        .map(Map.Entry::getKey)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** The candidate fields no tenant-active table stores yet. */
  static Set<JsonField> fieldsOnInactiveTables() {
    Set<String> active = activeTables();
    return jsonFieldsByTable().entrySet().stream()
        .filter(entry -> entry.getValue().stream().noneMatch(active::contains))
        .map(Map.Entry::getKey)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** "Class.field @ table[, table]" for every given field, for a readable assertion message. */
  static Set<String> describe(Set<JsonField> fields) {
    Map<JsonField, Set<String>> tables = jsonFieldsByTable();
    return fields.stream()
        .map(field -> field.key() + " @ " + String.join(",", tables.getOrDefault(field, Set.of())))
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
