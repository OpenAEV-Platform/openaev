package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.StreamAssetReference;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Base;
import io.openaev.database.model.Endpoint;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * The stream mirrors {@link MarkingDerivedTables} by hand, on the streamed entities (see {@link
 * StreamAssetReference}). This test reads the registry and the JPA annotations, and checks that
 * each streamed entity derived from an asset declares the matching rule, so that a change on one
 * side without the other fails CI instead of the stream sending what the SQL hides.
 */
@DisplayName("MarkingDerivedTables is mirrored on the stream")
class MarkingDerivedTablesStreamTest {

  private static final String ASSETS_TABLE = "assets";
  private static final String MARKING = "tlp-red";

  /**
   * Payload properties listing assets that are not tables, hence not in the registry: filtered on
   * the stream like the registry's links.
   */
  private static final Map<String, Set<String>> COMPUTED_ASSET_PROPERTIES =
      Map.of("AssetGroup", Set.of("asset_group_dynamic_assets"));

  private static final List<Class<?>> ENTITIES = concreteEntities();

  // -- Registry → entities --

  @Test
  @DisplayName("an entity belonging to one asset is hidden with it")
  void given_entryOnOneAsset_should_carryTheAssetsMarkings() throws Exception {
    for (MarkedTable entry : entriesOnOneAsset()) {
      String column = entry.parent().foreignKeyColumn();
      List<Class<?>> owners = streamedEntitiesOf(entry.table(), column);
      assertThat(owners)
          .as("%s.%s: no streamed entity maps it", entry.table(), column)
          .isNotEmpty();
      for (Class<?> owner : owners) {
        // Arrange
        Base entity = instantiate(owner);
        set(entity, assetField(owner, column), markedAsset());

        // Act
        String[] markings = entity.getStreamMarkings();

        // Assert
        assertThat(markings)
            .as(
                "%s maps %s.%s: getStreamMarkings() must return the markings of its asset",
                owner.getSimpleName(), entry.table(), column)
            .containsExactly(MARKING);
      }
    }
  }

  @Test
  @DisplayName("an entity listing assets declares the link, about when its own table is derived")
  void given_linkTable_should_beAStreamAssetReferenceOfItsKind() throws Exception {
    for (MarkedTable entry : entriesOnLinkTables()) {
      List<Field> fields = joinTableFields(entry.table());
      for (Field field : fields) {
        for (Class<?> owner : streamedEntitiesDeclaring(field)) {
          // Arrange
          Base entity = instantiate(owner);
          set(entity, field, new ArrayList<>(List.of(markedAsset())));
          String property = field.getAnnotation(JsonProperty.class).value();
          boolean about = isFilteredThroughLinkRows(tableOf(owner), entry.table());

          // Act
          List<StreamAssetReference> references = entity.getStreamAssetReferences();

          // Assert
          assertThat(references)
              .as(
                  "%s maps %s: getStreamAssetReferences() must return %s(\"%s\", %s)",
                  owner.getSimpleName(),
                  entry.table(),
                  about ? "about" : "listing",
                  property,
                  field.getName())
              .anySatisfy(
                  reference -> {
                    assertThat(reference.property()).isEqualTo(property);
                    assertThat(reference.markingsByAssetId())
                        .containsEntry("asset-id", Set.of(MARKING));
                    assertThat(reference.hidesEntityWhenAllHidden()).isEqualTo(about);
                  });
        }
      }
    }
  }

  @Test
  @DisplayName("an entity derived through another table is not streamed")
  void given_chainedEntry_should_notBeStreamed() {
    for (MarkedTable entry : MarkingDerivedTables.ALL) {
      if (entry.parent() == null || ASSETS_TABLE.equals(entry.parent().parentTable())) {
        continue;
      }
      // The stream only carries the markings of the asset an entity points to directly.
      assertThat(ENTITIES.stream().filter(e -> entry.table().equals(tableOf(e))))
          .as(
              "%s is derived through %s: not supported on the stream",
              entry.table(), entry.linkedTable())
          .noneMatch(MarkingDerivedTablesStreamTest::isStreamed);
    }
  }

  // -- Entities → registry --

  @Test
  @DisplayName("an entity filtered on the stream is filtered in SQL")
  void given_streamOverride_should_beInTheRegistry() throws Exception {
    Set<Class<?>> onOneAsset = new HashSet<>();
    for (MarkedTable entry : entriesOnOneAsset()) {
      onOneAsset.addAll(streamedEntitiesOf(entry.table(), entry.parent().foreignKeyColumn()));
    }
    for (Class<?> entity : ENTITIES) {
      if (Asset.class.isAssignableFrom(entity) || !isStreamed(entity)) {
        continue;
      }
      if (overrides(entity, "getStreamMarkings")) {
        assertThat(onOneAsset)
            .as(
                "%s overrides getStreamMarkings() but no registry entry maps it",
                entity.getSimpleName())
            .contains(entity);
      }
      if (overrides(entity, "getStreamAssetReferences")) {
        // Act
        Set<String> properties =
            instantiate(entity).getStreamAssetReferences().stream()
                .map(StreamAssetReference::property)
                .collect(Collectors.toSet());

        // Assert
        Set<String> expected = new HashSet<>(registryProperties(entity));
        expected.addAll(COMPUTED_ASSET_PROPERTIES.getOrDefault(entity.getSimpleName(), Set.of()));
        assertThat(expected)
            .as(
                "%s lists %s on the stream: each must be a link table of the registry",
                entity.getSimpleName(), properties)
            .containsAll(properties);
      }
    }
  }

  // -- Registry --

  /** {@code linkedTo(table, column, "assets", …)} where the table is an entity's. */
  private static List<MarkedTable> entriesOnOneAsset() {
    return MarkingDerivedTables.ALL.stream()
        .filter(entry -> entry.parent() != null)
        .filter(entry -> ASSETS_TABLE.equals(entry.parent().parentTable()))
        .filter(entry -> joinTableFields(entry.table()).isEmpty())
        .toList();
  }

  /** {@code linkedTo(linkTable, column, "assets", …)} where the table is a {@code @JoinTable}. */
  private static List<MarkedTable> entriesOnLinkTables() {
    return MarkingDerivedTables.ALL.stream()
        .filter(entry -> entry.parent() != null)
        .filter(entry -> ASSETS_TABLE.equals(entry.parent().parentTable()))
        .filter(entry -> !joinTableFields(entry.table()).isEmpty())
        .toList();
  }

  private static boolean isFilteredThroughLinkRows(String table, String linkTable) {
    return MarkingDerivedTables.ALL.stream()
        .anyMatch(
            entry ->
                entry.linkRows() != null
                    && entry.table().equals(table)
                    && entry.linkRows().linkTable().equals(linkTable));
  }

  private static Set<String> registryProperties(Class<?> entity) {
    return entriesOnLinkTables().stream()
        .flatMap(entry -> joinTableFields(entry.table()).stream())
        .filter(field -> field.getDeclaringClass().isAssignableFrom(entity))
        .map(field -> field.getAnnotation(JsonProperty.class).value())
        .collect(Collectors.toSet());
  }

  // -- JPA mapping --

  private static List<Class<?>> concreteEntities() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    return scanner.findCandidateComponents("io.openaev.database.model").stream()
        .<Class<?>>map(
            definition -> {
              try {
                return (Class<?>) Class.forName(definition.getBeanClassName());
              } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
              }
            })
        .filter(type -> !Modifier.isAbstract(type.getModifiers()))
        .toList();
  }

  private static String tableOf(Class<?> entity) {
    for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
      Table table = type.getAnnotation(Table.class);
      if (table != null) {
        return table.name().toLowerCase(Locale.ROOT);
      }
    }
    return null;
  }

  private static boolean isStreamed(Class<?> entity) {
    for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
      EntityListeners listeners = type.getAnnotation(EntityListeners.class);
      if (listeners != null && Arrays.asList(listeners.value()).contains(ModelBaseListener.class)) {
        return true;
      }
    }
    return false;
  }

  private static Stream<Field> fieldsOf(Class<?> entity) {
    Stream<Field> fields = Stream.empty();
    for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
      fields = Stream.concat(fields, Arrays.stream(type.getDeclaredFields()));
    }
    return fields;
  }

  /** The concrete streamed entities of the table holding an asset under that join column. */
  private static List<Class<?>> streamedEntitiesOf(String table, String column) {
    return ENTITIES.stream()
        .filter(entity -> table.equals(tableOf(entity)))
        .filter(MarkingDerivedTablesStreamTest::isStreamed)
        .filter(entity -> assetField(entity, column) != null)
        .toList();
  }

  private static Field assetField(Class<?> entity, String column) {
    return fieldsOf(entity)
        .filter(field -> Asset.class.isAssignableFrom(field.getType()))
        .filter(field -> field.isAnnotationPresent(JoinColumn.class))
        .filter(field -> column.equals(field.getAnnotation(JoinColumn.class).name()))
        .findFirst()
        .orElse(null);
  }

  private static List<Field> joinTableFields(String linkTable) {
    return ENTITIES.stream()
        .flatMap(MarkingDerivedTablesStreamTest::fieldsOf)
        .filter(field -> field.isAnnotationPresent(JoinTable.class))
        .filter(field -> linkTable.equals(field.getAnnotation(JoinTable.class).name()))
        // The owner's side lists assets; an asset's side of the same table lists the owners.
        .filter(MarkingDerivedTablesStreamTest::listsAssets)
        .distinct()
        .toList();
  }

  private static boolean listsAssets(Field field) {
    return field.getGenericType() instanceof ParameterizedType collection
        && collection.getActualTypeArguments()[0] instanceof Class<?> element
        && Asset.class.isAssignableFrom(element);
  }

  private static List<Class<?>> streamedEntitiesDeclaring(Field field) {
    return ENTITIES.stream()
        .filter(entity -> field.getDeclaringClass().isAssignableFrom(entity))
        .filter(MarkingDerivedTablesStreamTest::isStreamed)
        .toList();
  }

  private static boolean overrides(Class<?> entity, String method) throws NoSuchMethodException {
    return entity.getMethod(method).getDeclaringClass() != Base.class;
  }

  // -- Instances --

  private static Endpoint markedAsset() {
    Endpoint asset = new Endpoint();
    asset.setId("asset-id");
    asset.setMarkingIds(new String[] {MARKING});
    return asset;
  }

  private static Base instantiate(Class<?> entity) throws Exception {
    var constructor = entity.getDeclaredConstructor();
    constructor.setAccessible(true);
    return (Base) constructor.newInstance();
  }

  private static void set(Object entity, Field field, Object value) throws Exception {
    Objects.requireNonNull(field, "no field to set");
    field.setAccessible(true);
    field.set(entity, value);
  }
}
