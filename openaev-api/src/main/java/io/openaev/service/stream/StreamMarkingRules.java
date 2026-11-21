package io.openaev.service.stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.openaev.config.MarkedTable;
import io.openaev.config.MarkedTables;
import io.openaev.database.model.Base;
import io.openaev.helper.MultiModelSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The marking rules the stream applies to each entity, read from the active marked tables (the
 * registry {@code MarkingDerivedTables} included) and the JPA and JSON mapping: the stream shows a
 * reader what the SQL filtering shows them.
 *
 * <ul>
 *   <li><b>Row</b>: whether the entity is sent, by its own markings (an asset), the row it points
 *       to (an agent, a trace), or its link rows (a finding, hidden when all its assets are).
 *   <li><b>Listings</b>: the ids of marked or derived rows in the payload, at any depth, keep only
 *       the visible ones (an inject's targets, a finding's groups' assets).
 *   <li><b>Embedded</b>: the objects of a marked or derived table in the payload, alone or in an
 *       array, keep only the visible ones (an inject's expectations and traces, a collector's
 *       security platform).
 * </ul>
 *
 * The markings are read after commit ({@link StreamVisibilityService}).
 */
public class StreamMarkingRules {

  /**
   * How the rows of one marked or derived table are marked, read from a payload (an entity's, or an
   * embedded object's).
   *
   * @param table the table
   * @param markingsProperty the payload property holding the row's own markings, for a marked table
   * @param parentProperty the payload property holding the id of the row it points to (null when
   *     the entity has no such field: like a NULL foreign key in SQL, the row is visible)
   * @param about whether the row is marked by its link rows: {@code parentProperty} then lists the
   *     ids of the rows the links point to
   * @param parentTable the table of the rows pointed to
   */
  public record RowRule(
      String table,
      String markingsProperty,
      String parentProperty,
      boolean about,
      String parentTable) {

    public static RowRule ownMarkings(String table, String markingsProperty) {
      return new RowRule(table, markingsProperty, null, false, null);
    }

    public static RowRule parent(String table, String parentProperty, String parentTable) {
      return new RowRule(table, null, parentProperty, false, parentTable);
    }

    public static RowRule about(String table, String linkProperty, String parentTable) {
      return new RowRule(table, null, linkProperty, true, parentTable);
    }
  }

  /**
   * Ids of a marked or derived table listed in a payload.
   *
   * @param path the JSON path of the list, dot-separated, through nested objects and arrays (e.g.
   *     {@code finding_asset_groups.asset_group_assets})
   * @param table the table of the listed rows
   */
  public record ListingRule(String path, String table) {}

  /**
   * Objects of a marked or derived table embedded whole in a payload, alone or in an array.
   *
   * @param path the JSON path of the object or array, dot-separated (e.g. {@code
   *     inject_status.status_traces})
   * @param idProperty the property identifying an element
   * @param table the table of the elements, whose {@link RowRule} each element follows
   */
  public record EmbeddedRule(String path, String idProperty, String table) {}

  /** The rules of one entity class; empty for most, which the stream sends unfiltered. */
  public record EntityRule(RowRule row, List<ListingRule> listings, List<EmbeddedRule> embedded) {

    static final EntityRule NONE = new EntityRule(null, List.of(), List.of());

    public boolean isEmpty() {
      return row == null && listings.isEmpty() && embedded.isEmpty();
    }
  }

  /**
   * Where to read, by id, the markings of the rows of a table another row points to.
   *
   * @param entityName the JPQL name of the table's entity
   * @param idAttribute its id attribute
   * @param markingsAttribute the attribute holding the markings, for a marked table
   * @param parentAttribute the attribute pointing to the parent row, for a derived table
   * @param parentIdAttribute the id attribute of the parent's entity
   * @param parentTable the parent's table
   */
  public record RowSource(
      String entityName,
      String idAttribute,
      String markingsAttribute,
      String parentAttribute,
      String parentIdAttribute,
      String parentTable) {}

  private final MarkedTables markedTables;
  private final ObjectMapper mapper;
  private final Map<String, List<Class<?>>> entitiesByTable = new ConcurrentHashMap<>();
  private final Map<Class<?>, EntityRule> byEntity = new ConcurrentHashMap<>();
  private final Map<String, RowRule> byTable = new ConcurrentHashMap<>();

  public StreamMarkingRules(
      MarkedTables markedTables, Collection<Class<?>> entities, ObjectMapper mapper) {
    this.markedTables = markedTables;
    this.mapper = mapper;
    for (Class<?> entity : entities) {
      String table = tableOf(entity);
      if (table != null && markedTables.get(table) != null) {
        entitiesByTable.computeIfAbsent(table, t -> new ArrayList<>()).add(entity);
      }
    }
  }

  public EntityRule forEntity(Class<?> entity) {
    if (markedTables.tableNames().isEmpty()) {
      return EntityRule.NONE;
    }
    return byEntity.computeIfAbsent(entity, this::ruleOf);
  }

  /**
   * The rule of a table's rows, from the entity class mapped to it that holds the property: an
   * embedded element of another class of the table lacks it, and is visible (like a NULL foreign
   * key in SQL).
   */
  public RowRule forTable(String table) {
    return byTable.computeIfAbsent(
        table,
        t ->
            entitiesByTable.getOrDefault(t, List.of()).stream()
                .map(this::rowRuleOf)
                .filter(rule -> rule.markingsProperty() != null || rule.parentProperty() != null)
                .findFirst()
                .orElse(RowRule.parent(t, null, null)));
  }

  /** Where the markings of a table's rows are read by id; empty when they cannot be. */
  public Optional<RowSource> sourceOf(String table) {
    MarkedTable marked = markedTables.get(table);
    Class<?> root = rootEntityOf(table);
    if (marked == null || root == null || marked.linkRows() != null) {
      return Optional.empty();
    }
    String idAttribute = idFieldOf(root);
    if (marked.markingColumn() != null) {
      return fieldWith(root, Column.class, marked.markingColumn())
          .map(field -> new RowSource(entityName(root), idAttribute, field, null, null, null));
    }
    String parentTable = marked.parent().parentTable();
    Class<?> parentRoot = rootEntityOf(parentTable);
    if (parentRoot == null) {
      return Optional.empty();
    }
    return fieldWith(root, JoinColumn.class, marked.parent().foreignKeyColumn())
        .map(
            field ->
                new RowSource(
                    entityName(root),
                    idAttribute,
                    null,
                    field,
                    idFieldOf(parentRoot),
                    parentTable));
  }

  // -- Derivation --

  private EntityRule ruleOf(Class<?> entity) {
    RowRule row = rowRuleOf(entity);
    List<ListingRule> listings = new ArrayList<>();
    List<EmbeddedRule> embedded = new ArrayList<>();
    collectNested(entity, "", new HashSet<>(), listings, embedded);
    if (row == null && listings.isEmpty() && embedded.isEmpty()) {
      return EntityRule.NONE;
    }
    return new EntityRule(row, List.copyOf(listings), List.copyOf(embedded));
  }

  private RowRule rowRuleOf(Class<?> entity) {
    String table = tableOf(entity);
    MarkedTable marked = table == null ? null : markedTables.get(table);
    if (marked == null) {
      return null;
    }
    if (marked.markingColumn() != null) {
      return RowRule.ownMarkings(
          table, propertyWith(entity, Column.class, marked.markingColumn()).orElse(null));
    }
    if (marked.parent() != null) {
      return RowRule.parent(
          table,
          propertyWith(entity, JoinColumn.class, marked.parent().foreignKeyColumn()).orElse(null),
          marked.parent().parentTable());
    }
    String linkTable = marked.linkRows().linkTable();
    MarkedTable link = markedTables.get(linkTable);
    return RowRule.about(
        table,
        propertyWith(entity, JoinTable.class, linkTable).orElse(null),
        link == null ? null : link.linkedTable());
  }

  /**
   * The ids of marked or derived rows the payload lists, and the objects of a marked or derived
   * table it embeds whole, at any depth: an object serialized whole (a finding's groups) is walked
   * into.
   */
  private void collectNested(
      Class<?> type,
      String prefix,
      Set<Class<?>> walked,
      List<ListingRule> listings,
      List<EmbeddedRule> embedded) {
    if (!walked.add(type)) {
      return;
    }
    for (BeanPropertyDefinition property : propertiesOf(type)) {
      String path = prefix + property.getName();
      String table = tableOfContent(property);
      if (table != null && isIdList(property)) {
        if (canBeHidden(contentClassOf(property), table)) {
          listings.add(new ListingRule(path, table));
        }
        continue;
      }
      Class<?> content = contentClassOf(property);
      if (content == null || !content.isAnnotationPresent(Entity.class) || !isWhole(property)) {
        continue;
      }
      if (table != null) {
        embedded.add(new EmbeddedRule(path, idPropertyOf(content), table));
      } else {
        collectNested(content, path + ".", walked, listings, embedded);
      }
    }
    walked.remove(type);
  }

  /**
   * Whether a row of this class can be hidden: a row of a table derived through a parent is visible
   * when its class has no field for the parent link (a table-top expectation has no asset).
   */
  private boolean canBeHidden(Class<?> listed, String table) {
    MarkedTable marked = markedTables.get(table);
    if (marked.parent() == null) {
      return true;
    }
    String foreignKey = marked.parent().foreignKeyColumn();
    return entitiesByTable.getOrDefault(table, List.of()).stream()
        .filter(listed::isAssignableFrom)
        .anyMatch(entity -> fieldWith(entity, JoinColumn.class, foreignKey).isPresent());
  }

  // -- JSON mapping --

  private List<BeanPropertyDefinition> propertiesOf(Class<?> type) {
    BeanDescription description =
        mapper.getSerializationConfig().introspect(mapper.constructType(type));
    return description.findProperties().stream()
        .filter(BeanPropertyDefinition::couldSerialize)
        .filter(property -> !hasAnnotation(property, JsonIgnore.class))
        .toList();
  }

  private static <A extends java.lang.annotation.Annotation> boolean hasAnnotation(
      BeanPropertyDefinition property, Class<A> annotation) {
    AnnotatedMember accessor = property.getAccessor();
    return accessor != null && accessor.getAnnotation(annotation) != null;
  }

  /** Serialized as ids (an id-only serializer): the payload lists the rows, not their content. */
  private static boolean isIdList(BeanPropertyDefinition property) {
    JsonSerialize serializer = property.getAccessor().getAnnotation(JsonSerialize.class);
    return property.getPrimaryType().isCollectionLikeType()
        && serializer != null
        && serializer.using() != MultiModelSerializer.class
        && serializer.using() != JsonSerializer.None.class;
  }

  /** Serialized as whole objects: no serializer, or the one that writes each model in full. */
  private static boolean isWhole(BeanPropertyDefinition property) {
    JsonSerialize serializer = property.getAccessor().getAnnotation(JsonSerialize.class);
    return serializer == null
        || serializer.using() == MultiModelSerializer.class
        || serializer.using() == JsonSerializer.None.class;
  }

  private String tableOfContent(BeanPropertyDefinition property) {
    Class<?> content = contentClassOf(property);
    String table = content == null ? null : tableOf(content);
    return table != null && markedTables.get(table) != null ? table : null;
  }

  private static Class<?> contentClassOf(BeanPropertyDefinition property) {
    JavaType type = property.getPrimaryType();
    // An Optional getter (Inject.getStatus) serializes what it holds.
    if (type.isReferenceType()) {
      type = type.getReferencedType();
    }
    if (type.isCollectionLikeType() || type.isArrayType()) {
      return type.getContentType().getRawClass();
    }
    return type.getRawClass();
  }

  private <A extends java.lang.annotation.Annotation> Optional<String> propertyWith(
      Class<?> entity, Class<A> annotation, String column) {
    return propertiesOf(entity).stream()
        .filter(property -> column.equals(columnOf(property.getAccessor(), annotation)))
        .map(BeanPropertyDefinition::getName)
        .findFirst();
  }

  private String idPropertyOf(Class<?> entity) {
    return propertiesOf(entity).stream()
        .filter(property -> hasAnnotation(property, Id.class))
        .map(BeanPropertyDefinition::getName)
        .findFirst()
        .orElse(null);
  }

  // -- JPA mapping --

  private static String columnOf(AnnotatedMember member, Class<?> annotation) {
    if (member == null) {
      return null;
    }
    if (annotation == Column.class && member.getAnnotation(Column.class) != null) {
      return lower(member.getAnnotation(Column.class).name());
    }
    if (annotation == JoinColumn.class && member.getAnnotation(JoinColumn.class) != null) {
      return lower(member.getAnnotation(JoinColumn.class).name());
    }
    if (annotation == JoinTable.class && member.getAnnotation(JoinTable.class) != null) {
      return lower(member.getAnnotation(JoinTable.class).name());
    }
    return null;
  }

  private static <A extends java.lang.annotation.Annotation> Optional<String> fieldWith(
      Class<?> entity, Class<A> annotation, String column) {
    for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
      for (java.lang.reflect.Field field : type.getDeclaredFields()) {
        A found = field.getAnnotation(annotation);
        String name =
            found instanceof Column c ? c.name() : found instanceof JoinColumn j ? j.name() : null;
        if (name != null && column.equals(lower(name))) {
          return Optional.of(field.getName());
        }
      }
    }
    return Optional.empty();
  }

  private static String idFieldOf(Class<?> entity) {
    for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
      for (java.lang.reflect.Field field : type.getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
          return field.getName();
        }
      }
    }
    throw new IllegalStateException(entity.getSimpleName() + " has no @Id field");
  }

  /** The topmost entity class of a table: the one JPQL reads its rows through. */
  private Class<?> rootEntityOf(String table) {
    return entitiesByTable.getOrDefault(table, List.of()).stream()
        .filter(
            entity ->
                entity.getSuperclass() == null || !table.equals(tableOf(entity.getSuperclass())))
        .findFirst()
        .orElse(null);
  }

  private static String entityName(Class<?> entity) {
    Entity annotation = entity.getAnnotation(Entity.class);
    return annotation != null && !annotation.name().isEmpty()
        ? annotation.name()
        : entity.getSimpleName();
  }

  static String tableOf(Class<?> entity) {
    for (Class<?> type = entity;
        type != null && Base.class.isAssignableFrom(type);
        type = type.getSuperclass()) {
      Table table = type.getAnnotation(Table.class);
      if (table != null) {
        return lower(table.name());
      }
    }
    return null;
  }

  private static String lower(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
