package io.openaev.architecture;

import static io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.jsonFieldsByTable;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.JsonField;
import io.openaev.database.model.PrimitiveType;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every type stored in a JSON-mapped column has value equality, at every level it nests.
 *
 * <p>Without it, Hibernate's dirty check cannot see a loaded value as equal to its own copy, so a
 * plain read flushes an {@code UPDATE} of the row on every flush. The rule proves the shape; {@link
 * JsonTypeDirtyOnLoadProbeTest} proves the effect on a real row. The two disagree on purpose in one
 * case: hypersistence falls back to a JSON tree comparison for a single value whose class declares
 * no {@code equals}, which is safe today, but declaring {@code equals} on that class alone, or
 * moving the field to the native mapping, makes it live. The rule therefore asks for equality at
 * every level, whatever the mapping. {@link JsonValueEqualityRules} says what the walk cannot see.
 */
@DisplayName("every type stored in a JSON column has value equality")
class JsonValueEqualityArchTest {

  /**
   * Types known to break the rule, each with the JSON-mapped fields that reach it. A ratchet, not a
   * budget: the build fails when a type is added, when a new field reaches a listed type, and when
   * a type is fixed and its line is left here.
   *
   * <p>{@code Widget.widgetConfiguration} holds a single polymorphic value on the hypersistence
   * mapping, which compares it as a JSON tree because no class of the hierarchy declares {@code
   * equals}, and the probe measures zero updates on load. Fixing it means equality on every level
   * of a four-level hierarchy at once, which is its own change.
   */
  private static final Map<String, Set<String>> BASELINE =
      Map.ofEntries(
          widgetConfiguration("WidgetConfiguration"),
          widgetConfiguration("WidgetConfigurationWithSeries"),
          widgetConfiguration("HistogramWidget"),
          widgetConfiguration("DateHistogramWidget"),
          widgetConfiguration("StructuralHistogramWidget"),
          widgetConfiguration("FlatConfiguration"),
          widgetConfiguration("AverageConfiguration"),
          widgetConfiguration("ListConfiguration"),
          widgetConfiguration("EngineSortField"));

  private static Map.Entry<String, Set<String>> widgetConfiguration(String simpleName) {
    return Map.entry("io.openaev.engine.api." + simpleName, Set.of("Widget.widgetConfiguration"));
  }

  @Nested
  @DisplayName("the entity model")
  class EntityModel {

    @Test
    @DisplayName(
        "given every JSON-mapped field, when walked, then only baselined types lack equality")
    void given_every_json_mapped_field_should_reach_only_types_with_value_equality() {
      // Arrange
      Map<String, Field> fields = new TreeMap<>();
      for (JsonField field : jsonFieldsByTable().keySet()) {
        fields.put(field.key(), reflect(field));
      }

      // Act
      Map<String, Set<String>> violations =
          JsonValueEqualityRules.typesWithoutValueEquality(fields);

      // Assert
      assertThat(fields)
          .as("the scan found too few JSON-mapped fields to be right")
          .hasSizeGreaterThan(35);
      assertThat(violations)
          .as(
              "a type stored in a JSON column has no value equality, so loading a row that holds"
                  + " it rewrites the row. Give it equals and hashCode (Lombok @EqualsAndHashCode"
                  + " or @Data), together with every type it nests. Reached from: %s",
              violations)
          .isEqualTo(new TreeMap<>(BASELINE));
    }
  }

  @Nested
  @DisplayName("what the walk reports, on fixture types")
  class Walk {

    @Test
    @DisplayName("given a value type with equality at every level, when walked, then nothing")
    void given_value_equality_at_every_level_should_report_nothing() {
      // Act
      Set<String> violations = walkFieldOf(WithEqualityHolder.class);

      // Assert
      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("given a type without equality, when walked, then it is reported")
    void given_a_type_without_equality_should_report_it() {
      // Act
      Set<String> violations = walkFieldOf(WithoutEqualityHolder.class);

      // Assert
      assertThat(violations).containsExactly(WithoutEquality.class.getName());
    }

    @Test
    @DisplayName(
        "given a type with equality nesting a list of a type without, when walked, then the nested"
            + " type is reported")
    void given_equality_on_the_outer_type_only_should_report_the_nested_type() {
      // Act
      Set<String> violations = walkFieldOf(OuterOnlyHolder.class);

      // Assert
      assertThat(violations).containsExactly(WithoutEquality.class.getName());
    }

    @Test
    @DisplayName("given a map whose values lack equality, when walked, then the value is reported")
    void given_a_map_value_without_equality_should_report_it() {
      // Act
      Set<String> violations = walkFieldOf(MapValueHolder.class);

      // Assert
      assertThat(violations).containsExactly(WithoutEquality.class.getName());
    }

    @Test
    @DisplayName(
        "given a subclass with equality whose parent holds state without it, when walked, then the"
            + " parent is reported")
    void given_a_stateful_parent_without_equality_should_report_the_parent() {
      // Act
      Set<String> violations = walkFieldOf(SubclassHolder.class);

      // Assert
      assertThat(violations).containsExactly(StatefulParent.class.getName());
    }

    @Test
    @DisplayName(
        "given a polymorphic type, when walked, then each listed subtype is checked, not the base")
    void given_a_polymorphic_type_should_check_each_listed_subtype() {
      // Act
      Set<String> violations = walkFieldOf(PolymorphicHolder.class);

      // Assert
      assertThat(violations).containsExactly(SubtypeWithoutEquality.class.getName());
    }

    @Test
    @DisplayName(
        "given an interface without listed subtypes, when walked, then it is reported as unseen")
    void given_an_interface_without_listed_subtypes_should_report_it() {
      // Act
      Set<String> violations = walkFieldOf(UnlistedInterfaceHolder.class);

      // Assert
      assertThat(violations)
          .containsExactly(
              UnlistedInterface.class.getName() + " (interface without listed subtypes)");
    }

    @Test
    @DisplayName(
        "given ignored, open and JSON tree fields, when walked, then they are not reported")
    void given_ignored_open_and_json_tree_fields_should_report_nothing() {
      // Act
      Set<String> violations = walkFieldOf(SkippedFieldsHolder.class);

      // Assert
      assertThat(violations).isEmpty();
    }

    private Set<String> walkFieldOf(Class<?> holder) {
      try {
        return JsonValueEqualityRules.walk(
            holder.getDeclaredField("value").getGenericType(), new HashSet<>());
      } catch (NoSuchFieldException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  // -- fixture types: each holder's "value" field stands for a JSON-mapped entity field --

  @EqualsAndHashCode
  static class WithEquality {
    String name;
    List<String> tags;
    Instant at;
    PrimitiveType type;
  }

  static class WithoutEquality {
    String name;
  }

  @EqualsAndHashCode
  static class OuterOnly {
    List<WithoutEquality> blocks;
  }

  static class StatefulParent {
    String parentState;
  }

  @EqualsAndHashCode(callSuper = true)
  static class Subclass extends StatefulParent {
    String ownState;
  }

  @JsonSubTypes({
    @JsonSubTypes.Type(value = SubtypeWithEquality.class),
    @JsonSubTypes.Type(value = SubtypeWithoutEquality.class)
  })
  interface Polymorphic {}

  @EqualsAndHashCode
  static class SubtypeWithEquality implements Polymorphic {
    String a;
  }

  static class SubtypeWithoutEquality implements Polymorphic {
    String b;
  }

  interface UnlistedInterface {}

  @EqualsAndHashCode
  static class SkippedFields {
    @JsonIgnore WithoutEquality ignored;
    transient WithoutEquality notSerialized;
    static WithoutEquality shared;
    Object open;
    JsonNode tree;
    Map<String, Object> configuration;
  }

  static class WithEqualityHolder {
    List<WithEquality> value;
  }

  static class WithoutEqualityHolder {
    WithoutEquality value;
  }

  static class OuterOnlyHolder {
    OuterOnly value;
  }

  static class MapValueHolder {
    Map<String, List<WithoutEquality>> value;
  }

  static class SubclassHolder {
    Subclass value;
  }

  static class PolymorphicHolder {
    List<Polymorphic> value;
  }

  static class UnlistedInterfaceHolder {
    List<UnlistedInterface> value;
  }

  static class SkippedFieldsHolder {
    SkippedFields value;
  }

  private static Field reflect(JsonField field) {
    try {
      return field.declaringClass().getDeclaredField(field.fieldName());
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException("cannot read " + field, e);
    }
  }
}
