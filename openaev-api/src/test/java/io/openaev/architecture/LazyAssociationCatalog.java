package io.openaev.architecture;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiIdSetSerializer;
import io.openaev.helper.MultiModelSerializer;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Which HTTP responses carry a lazy association that Jackson initialises after the request's
 * transaction has committed, decided from the mappings alone.
 *
 * <p>An association is in the class when four things hold: it is lazy, Jackson serialises it,
 * serialising it loads it, and the load reads a table the statement inspector gates. "Serialising
 * loads it" is decided by the serializer, because the platform's mapper registers {@code
 * Hibernate7Module}: an uninitialised collection or proxy left to the module's default serializer
 * is written as {@code null} without any statement, whereas an id-list or model serializer iterates
 * the collection and loads it. A to-one written through {@link MonoIdSerializer} only asks the
 * proxy for its id, which does not load it. A {@code @JsonProperty} method computed from a lazy
 * field loads that field whatever the field's own serializer.
 *
 * <p>What this cannot see: a fetch join, an entity graph or an explicit initialisation in the code
 * path that produced the entity. Those decide whether the association is still lazy when Jackson
 * reaches it, and only a run can tell; the lazy-after-commit probe is that run. This catalog lists
 * the shape, the probe measures it.
 */
public final class LazyAssociationCatalog {

  /** How serialising an association behaves when it is still lazy. */
  public enum Serialization {
    /** Iterates the collection to write ids: loads it. */
    ID_LIST,
    /** Writes each element in full: loads it, and the elements' own associations are reached. */
    MODEL,
    /** A custom serializer this catalog does not know: assumed to load it. */
    OTHER,
    /** Asks a to-one proxy for its id only: does not load it. */
    ID_OF_PROXY,
    /**
     * Left to {@code Hibernate7Module}: an uninitialised value is written as null, no statement.
     */
    MODULE_DEFAULT,
    /** A {@code @JsonProperty} method computed from the association: loads it. */
    DERIVED
  }

  /**
   * A serialised association of an entity.
   *
   * @param declaringClass the class that declares the field or method
   * @param property the field name, or the method name for a derived property
   * @param sourceField the field holding the association, the one a derived property reads
   * @param targetEntity the entity at the other end, or {@code null} for an element collection
   * @param tables the tables a load of it reads
   * @param lazy whether it is still lazy when nothing initialised it
   * @param serialization how Jackson writes it
   */
  public record Association(
      Class<?> declaringClass,
      String property,
      String sourceField,
      Class<?> targetEntity,
      Set<String> tables,
      boolean lazy,
      Serialization serialization) {

    public String key() {
      return declaringClass.getSimpleName() + "." + property;
    }

    /** Whether serialising it while still lazy issues a statement. */
    public boolean loadsWhenSerialised() {
      return lazy
          && serialization != Serialization.ID_OF_PROXY
          && serialization != Serialization.MODULE_DEFAULT;
    }

    /** Whether it is written as {@code null} instead of its content when still lazy. */
    public boolean writtenAsNullWhenLazy() {
      return lazy && serialization == Serialization.MODULE_DEFAULT;
    }

    public boolean readsAnyOf(Set<String> activeTables) {
      return tables.stream().anyMatch(activeTables::contains);
    }
  }

  /** A controller method whose response body is serialised by Jackson. */
  public record Endpoint(Class<?> controller, Method method) {

    public String key() {
      return controller.getSimpleName() + "." + method.getName();
    }
  }

  /** One endpoint whose response reaches one association. */
  public record Site(Endpoint endpoint, Association association) {

    public String key() {
      return endpoint.key() + " -> " + association.key();
    }
  }

  private static final int MAX_DTO_DEPTH = 4;
  private static final Map<Class<?>, List<Association>> ASSOCIATIONS = new ConcurrentHashMap<>();
  private static volatile Set<Class<?>> entities;
  private static volatile JavaClasses entityBytecode;

  private LazyAssociationCatalog() {}

  // -- ENDPOINTS --

  /**
   * Every production controller method mapped to a route and answering with a Jackson body. Test
   * controllers are left out by where their class was loaded from.
   */
  public static List<Endpoint> productionEndpoints() {
    List<Endpoint> endpoints = new ArrayList<>();
    for (Class<?> controller : scan(Controller.class)) {
      if (isTestClass(controller)) {
        continue;
      }
      boolean classBody = AnnotatedElementUtils.hasAnnotation(controller, ResponseBody.class);
      for (Method method : ReflectionUtils.getUniqueDeclaredMethods(controller)) {
        if (method.isBridge()
            || method.isSynthetic()
            || !AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
          continue;
        }
        if (classBody || AnnotatedElementUtils.hasAnnotation(method, ResponseBody.class)) {
          endpoints.add(new Endpoint(controller, method));
        }
      }
    }
    endpoints.sort((a, b) -> a.key().compareTo(b.key()));
    return endpoints;
  }

  /** Whether the method's declared return type says nothing about what it serialises. */
  public static boolean opaqueResponse(Endpoint endpoint) {
    return opaque(endpoint.method().getGenericReturnType());
  }

  private static boolean opaque(Type type) {
    if (type instanceof Class<?> raw) {
      return raw == Object.class;
    }
    if (type instanceof ParameterizedType parameterized) {
      return Arrays.stream(parameterized.getActualTypeArguments())
          .anyMatch(LazyAssociationCatalog::opaque);
    }
    return type instanceof WildcardType || type instanceof TypeVariable<?>;
  }

  // -- SITES --

  /**
   * Every endpoint-association pair where the endpoint's response reaches an entity carrying an
   * association that loads when serialised and reads one of {@code activeTables}.
   */
  public static List<Site> sites(Set<String> activeTables) {
    List<Site> sites = new ArrayList<>();
    for (Endpoint endpoint : productionEndpoints()) {
      Set<Class<?>> entities = new LinkedHashSet<>();
      Set<Class<?>> dtos = new LinkedHashSet<>();
      collect(endpoint.method().getGenericReturnType(), entities, dtos, 0);
      documentedResponseTypes(endpoint.method()).forEach(type -> collect(type, entities, dtos, 0));
      List<Association> reached = new ArrayList<>();
      entities.forEach(entity -> reached.addAll(associationsOf(entity)));
      dtos.forEach(dto -> reached.addAll(carriedCollectionsOf(dto)));
      for (Association association : reached) {
        if (association.loadsWhenSerialised() && association.readsAnyOf(activeTables)) {
          sites.add(new Site(endpoint, association));
        }
      }
    }
    return dedupe(sites);
  }

  /**
   * The body types the method's OpenAPI responses declare. A method typed narrower than what it
   * answers (a page of one DTO that also returns another) documents the others there.
   */
  static Set<Class<?>> documentedResponseTypes(Method method) {
    Set<Class<?>> types = new LinkedHashSet<>();
    for (ApiResponse response :
        AnnotatedElementUtils.findMergedRepeatableAnnotations(
            method, ApiResponse.class, ApiResponses.class)) {
      for (Content content : response.content()) {
        addSchemaTypes(content.schema(), types);
        addSchemaTypes(content.array().schema(), types);
      }
    }
    types.remove(Void.class);
    return types;
  }

  private static void addSchemaTypes(Schema schema, Set<Class<?>> types) {
    types.add(schema.implementation());
    types.addAll(Arrays.asList(schema.oneOf()));
    types.addAll(Arrays.asList(schema.anyOf()));
  }

  /**
   * The entity collections a DTO serialises. A mapper that hands the entity's own collection to the
   * DTO, rather than a copy, leaves it lazy, so the DTO loads it when written exactly as the entity
   * would. Whether the mapper copies is not visible from the type, so every such field counts.
   */
  static List<Association> carriedCollectionsOf(Class<?> dto) {
    List<Association> carried = new ArrayList<>();
    for (Field field : allFields(dto)) {
      if (Modifier.isStatic(field.getModifiers())
          || !isCollection(field)
          || targetEntity(field) == null
          || !serialised(dto, field)) {
        continue;
      }
      carried.add(
          new Association(
              field.getDeclaringClass(),
              field.getName(),
              field.getName(),
              targetEntity(field),
              tablesRead(field),
              true,
              serialization(dto, field)));
    }
    return carried;
  }

  /** The keys of {@link #sites(Set)}, sorted. */
  public static Set<String> siteKeys(Set<String> activeTables) {
    Set<String> keys = new TreeSet<>();
    sites(activeTables).forEach(site -> keys.add(site.key()));
    return keys;
  }

  private static List<Site> dedupe(List<Site> sites) {
    Set<String> seen = new LinkedHashSet<>();
    List<Site> unique = new ArrayList<>();
    for (Site site : sites) {
      if (seen.add(site.key())) {
        unique.add(site);
      }
    }
    return unique;
  }

  // -- REACH --

  /**
   * The entity classes a value of {@code type} serialises as full objects, following generic
   * arguments, DTO fields and the associations an entity writes in full. Subclasses of a reached
   * entity are reached too: a response typed {@code Asset} may carry an {@code Endpoint}.
   */
  public static Set<Class<?>> entitiesReachedBy(Type type) {
    Set<Class<?>> reached = new LinkedHashSet<>();
    collect(type, reached, new LinkedHashSet<>(), 0);
    return reached;
  }

  private static void collect(
      Type type, Set<Class<?>> reached, Set<Class<?>> visitedDtos, int depth) {
    if (type instanceof ParameterizedType parameterized) {
      collect(parameterized.getRawType(), reached, visitedDtos, depth);
      for (Type argument : parameterized.getActualTypeArguments()) {
        collect(argument, reached, visitedDtos, depth);
      }
    } else if (type instanceof WildcardType wildcard) {
      Arrays.stream(wildcard.getUpperBounds())
          .forEach(b -> collect(b, reached, visitedDtos, depth));
    } else if (type instanceof GenericArrayType array) {
      collect(array.getGenericComponentType(), reached, visitedDtos, depth);
    } else if (type instanceof Class<?> raw) {
      if (raw.isArray()) {
        collect(raw.getComponentType(), reached, visitedDtos, depth);
      } else if (isEntity(raw)) {
        for (Class<?> entity : withEntitySubclasses(raw)) {
          if (reached.add(entity)) {
            for (Association association : associationsOf(entity)) {
              if (association.targetEntity() != null && writtenInFull(association)) {
                collect(association.targetEntity(), reached, visitedDtos, depth);
              }
            }
            collectFieldTypes(entity, reached, visitedDtos, depth);
          }
        }
      } else if (raw.isInterface() && raw.getName().startsWith("io.openaev.")) {
        // A documentation type such as a page interface carries its content in its supertypes.
        for (Type supertype : raw.getGenericInterfaces()) {
          collect(supertype, reached, visitedDtos, depth);
        }
      } else if (isApplicationType(raw) && depth < MAX_DTO_DEPTH && visitedDtos.add(raw)) {
        collectFieldTypes(raw, reached, visitedDtos, depth + 1);
      }
    }
  }

  /** Non-association serialised fields of application types: DTOs, embeddables, transients. */
  private static void collectFieldTypes(
      Class<?> type, Set<Class<?>> reached, Set<Class<?>> visitedDtos, int depth) {
    for (Field field : allFields(type)) {
      if (Modifier.isStatic(field.getModifiers())
          || isAssociation(field)
          || !serialised(type, field)) {
        continue;
      }
      collect(field.getGenericType(), reached, visitedDtos, depth);
    }
  }

  /**
   * A model serializer writes in full, and so does the default one whenever the value is loaded:
   * eagerly, or lazily but initialised by the handler, which the mapping cannot rule out.
   */
  private static boolean writtenInFull(Association association) {
    return association.serialization() == Serialization.MODEL
        || association.serialization() == Serialization.MODULE_DEFAULT;
  }

  // -- ASSOCIATIONS --

  /** The serialised associations of an entity, inherited ones included. */
  public static List<Association> associationsOf(Class<?> entity) {
    return ASSOCIATIONS.computeIfAbsent(entity, LazyAssociationCatalog::computeAssociations);
  }

  private static List<Association> computeAssociations(Class<?> entity) {
    List<Association> associations = new ArrayList<>();
    for (Field field : allFields(entity)) {
      if (!isAssociation(field) || !serialised(entity, field)) {
        continue;
      }
      associations.add(
          new Association(
              field.getDeclaringClass(),
              field.getName(),
              field.getName(),
              targetEntity(field),
              tablesRead(field),
              isLazy(field),
              serialization(entity, field)));
    }
    associations.addAll(derivedProperties(entity));
    return List.copyOf(associations);
  }

  /**
   * {@code @JsonProperty} methods whose body reads a lazy association, through the field or its
   * getter. Read from bytecode, since nothing in the method's signature says what it touches.
   */
  private static List<Association> derivedProperties(Class<?> entity) {
    Map<String, Field> lazyFields = new java.util.HashMap<>();
    for (Field field : allFields(entity)) {
      if (isAssociation(field) && isLazy(field)) {
        lazyFields.put(field.getDeclaringClass().getName() + "#" + field.getName(), field);
      }
    }
    if (lazyFields.isEmpty()) {
      return List.of();
    }
    List<Association> derived = new ArrayList<>();
    for (Class<?> type = entity;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      if (!entityBytecode().contain(type)) {
        continue;
      }
      JavaClass javaClass = entityBytecode().get(type);
      for (JavaMethod method : javaClass.getMethods()) {
        if (!method.isAnnotatedWith(JsonProperty.class)
            || method.isAnnotatedWith(JsonIgnore.class)
            || !method.getParameters().isEmpty()) {
          continue;
        }
        Set<Field> read = new LinkedHashSet<>();
        method
            .getFieldAccesses()
            .forEach(
                access -> {
                  Field field =
                      lazyFields.get(access.getTargetOwner().getName() + "#" + access.getName());
                  if (field != null) {
                    read.add(field);
                  }
                });
        method
            .getMethodCallsFromSelf()
            .forEach(
                call -> {
                  String name = call.getName();
                  if (name.startsWith("get") && name.length() > 3) {
                    String fieldName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    Field field = lazyFields.get(call.getTargetOwner().getName() + "#" + fieldName);
                    if (field == null) {
                      field = findInHierarchy(lazyFields, entity, fieldName);
                    }
                    if (field != null) {
                      read.add(field);
                    }
                  }
                });
        for (Field field : read) {
          if (method.getName().equals(getterName(field)) || onlyAsksForId(method, field)) {
            continue;
          }
          derived.add(
              new Association(
                  type,
                  method.getName() + "()",
                  field.getName(),
                  targetEntity(field),
                  tablesRead(field),
                  true,
                  Serialization.DERIVED));
        }
      }
    }
    return derived;
  }

  /**
   * Whether a derived method only asks a lazy to-one for its id. A proxy answers {@code getId()}
   * without loading, so such a method reads no table however often it touches the field.
   */
  private static boolean onlyAsksForId(JavaMethod method, Field field) {
    if (isCollection(field)) {
      return false;
    }
    Class<?> target = field.getType();
    return method.getMethodCallsFromSelf().stream()
        .filter(call -> isAssignable(call.getTargetOwner(), target))
        .allMatch(call -> call.getName().equals("getId"));
  }

  private static boolean isAssignable(JavaClass owner, Class<?> target) {
    try {
      return Class.forName(owner.getName()).isAssignableFrom(target);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static Field findInHierarchy(
      Map<String, Field> lazyFields, Class<?> entity, String name) {
    for (Class<?> type = entity;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      Field field = lazyFields.get(type.getName() + "#" + name);
      if (field != null) {
        return field;
      }
    }
    return null;
  }

  private static boolean isAssociation(Field field) {
    return field.isAnnotationPresent(OneToMany.class)
        || field.isAnnotationPresent(ManyToMany.class)
        || field.isAnnotationPresent(ManyToOne.class)
        || field.isAnnotationPresent(OneToOne.class)
        || field.isAnnotationPresent(ElementCollection.class);
  }

  private static boolean isLazy(Field field) {
    Fetch fetch = field.getAnnotation(Fetch.class);
    if (fetch != null && fetch.value() == FetchMode.JOIN) {
      return false;
    }
    FetchType type;
    if (field.isAnnotationPresent(OneToMany.class)) {
      type = field.getAnnotation(OneToMany.class).fetch();
    } else if (field.isAnnotationPresent(ManyToMany.class)) {
      type = field.getAnnotation(ManyToMany.class).fetch();
    } else if (field.isAnnotationPresent(ElementCollection.class)) {
      type = field.getAnnotation(ElementCollection.class).fetch();
    } else if (field.isAnnotationPresent(ManyToOne.class)) {
      type = field.getAnnotation(ManyToOne.class).fetch();
    } else {
      type = field.getAnnotation(OneToOne.class).fetch();
    }
    return type == FetchType.LAZY;
  }

  private static Serialization serialization(Class<?> entity, Field field) {
    JsonSerialize annotation = field.getAnnotation(JsonSerialize.class);
    Method getter = getter(entity, field);
    if (annotation == null && getter != null) {
      annotation = getter.getAnnotation(JsonSerialize.class);
    }
    Class<?> using = annotation == null ? null : annotation.using();
    if (using == null || using == JsonSerializerNone.TYPE) {
      return Serialization.MODULE_DEFAULT;
    }
    if (using == MonoIdSerializer.class) {
      return isCollection(field) ? Serialization.ID_LIST : Serialization.ID_OF_PROXY;
    }
    if (using == MultiIdListSerializer.class || using == MultiIdSetSerializer.class) {
      return Serialization.ID_LIST;
    }
    if (using == MultiModelSerializer.class) {
      return Serialization.MODEL;
    }
    return Serialization.OTHER;
  }

  /** {@code JsonSerialize.None}, the annotation's "no serializer" default. */
  private static final class JsonSerializerNone {
    static final Class<?> TYPE = com.fasterxml.jackson.databind.JsonSerializer.None.class;
  }

  private static boolean isCollection(Field field) {
    return Collection.class.isAssignableFrom(field.getType())
        || Map.class.isAssignableFrom(field.getType());
  }

  /** The entity at the other end, or {@code null} for a collection of values. */
  private static Class<?> targetEntity(Field field) {
    Class<?> target = field.getType();
    if (isCollection(field) && field.getGenericType() instanceof ParameterizedType parameterized) {
      Type[] arguments = parameterized.getActualTypeArguments();
      Type element = arguments[arguments.length - 1];
      target = element instanceof Class<?> raw ? raw : null;
    }
    return target != null && isEntity(target) ? target : null;
  }

  /**
   * The tables a load of this association reads: the target's table, its join table, and the tables
   * of the target's eager associations, which the same load fetches.
   */
  private static Set<String> tablesRead(Field field) {
    Set<String> tables = new TreeSet<>();
    Class<?> target = targetEntity(field);
    if (target != null) {
      addEagerTables(target, tables, new LinkedHashSet<>());
    }
    JoinTable joinTable = field.getAnnotation(JoinTable.class);
    if (joinTable != null && !joinTable.name().isBlank()) {
      tables.add(joinTable.name().toLowerCase(Locale.ROOT));
    }
    CollectionTable collectionTable = field.getAnnotation(CollectionTable.class);
    if (collectionTable != null && !collectionTable.name().isBlank()) {
      tables.add(collectionTable.name().toLowerCase(Locale.ROOT));
    }
    return tables;
  }

  /** The entity's table and, transitively, those of its eager associations, serialised or not. */
  private static void addEagerTables(Class<?> entity, Set<String> tables, Set<Class<?>> visited) {
    if (!visited.add(entity)) {
      return;
    }
    tables.add(JsonTypeDirtyOnLoadCatalog.tableOf(entity));
    for (Field field : allFields(entity)) {
      if (!isAssociation(field) || isLazy(field)) {
        continue;
      }
      JoinTable joinTable = field.getAnnotation(JoinTable.class);
      if (joinTable != null && !joinTable.name().isBlank()) {
        tables.add(joinTable.name().toLowerCase(Locale.ROOT));
      }
      Class<?> target = targetEntity(field);
      if (target != null) {
        addEagerTables(target, tables, visited);
      }
    }
  }

  // -- JACKSON VISIBILITY --

  /**
   * Whether Jackson writes this field. Mirrors the rules this codebase relies on: an ignored field
   * or getter is not written, a write-only property is not written, a {@code @JsonProperty} field
   * is written, and otherwise a field is written when it has a public getter.
   */
  static boolean serialised(Class<?> type, Field field) {
    if (ignored(field.getAnnotation(JsonIgnore.class))) {
      return false;
    }
    Method getter = getter(type, field);
    if (getter != null && ignored(getter.getAnnotation(JsonIgnore.class))) {
      return false;
    }
    JsonProperty property = field.getAnnotation(JsonProperty.class);
    if (property == null && getter != null) {
      property = getter.getAnnotation(JsonProperty.class);
    }
    if (property != null && property.access() == JsonProperty.Access.WRITE_ONLY) {
      return false;
    }
    return property != null || getter != null || Modifier.isPublic(field.getModifiers());
  }

  private static boolean ignored(JsonIgnore ignore) {
    return ignore != null && ignore.value();
  }

  private static Method getter(Class<?> type, Field field) {
    Method method = ReflectionUtils.findMethod(type, getterName(field));
    if (method == null && (field.getType() == boolean.class)) {
      String name = field.getName();
      method =
          ReflectionUtils.findMethod(
              type, "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
    }
    return method != null && Modifier.isPublic(method.getModifiers()) ? method : null;
  }

  private static String getterName(Field field) {
    String name = field.getName();
    return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private static List<Field> allFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      fields.addAll(Arrays.asList(current.getDeclaredFields()));
    }
    return fields;
  }

  // -- CLASSPATH --

  public static boolean isEntity(Class<?> type) {
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      if (current.isAnnotationPresent(Entity.class)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isApplicationType(Class<?> type) {
    return type.getName().startsWith("io.openaev.")
        && !type.isEnum()
        && !type.isInterface()
        && !type.isPrimitive();
  }

  private static List<Class<?>> withEntitySubclasses(Class<?> entity) {
    List<Class<?>> family = new ArrayList<>();
    family.add(entity);
    for (Class<?> candidate : entities()) {
      if (candidate != entity && entity.isAssignableFrom(candidate)) {
        family.add(candidate);
      }
    }
    return family;
  }

  private static Set<Class<?>> entities() {
    Set<Class<?>> local = entities;
    if (local == null) {
      local = JsonTypeDirtyOnLoadCatalog.mappedEntities();
      entities = local;
    }
    return local;
  }

  private static JavaClasses entityBytecode() {
    JavaClasses local = entityBytecode;
    if (local == null) {
      local = new ClassFileImporter().importClasses(entities().toArray(Class<?>[]::new));
      entityBytecode = local;
    }
    return local;
  }

  private static boolean isTestClass(Class<?> type) {
    var source = type.getProtectionDomain().getCodeSource();
    return source != null && source.getLocation().getPath().contains("/test-classes/");
  }

  private static List<Class<?>> scan(Class<? extends java.lang.annotation.Annotation> annotation) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return true;
          }
        };
    scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
    List<Class<?>> classes = new ArrayList<>();
    for (BeanDefinition definition : scanner.findCandidateComponents("io.openaev")) {
      try {
        classes.add(Class.forName(definition.getBeanClassName()));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("cannot load " + definition.getBeanClassName(), e);
      }
    }
    return classes;
  }
}
