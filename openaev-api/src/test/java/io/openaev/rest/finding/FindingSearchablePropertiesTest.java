package io.openaev.rest.finding;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Finding;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #8176: the occurrences listed on a finding detail page all share the finding's type, field, value
 * and name, so the text search must also match the context of each occurrence.
 */
@DisplayName("Finding searchable properties")
class FindingSearchablePropertiesTest {

  private static Map<String, PropertySchema> searchableByName() {
    List<PropertySchema> searchable =
        SchemaUtils.getSearchableProperties(SchemaUtils.schema(Finding.class));
    return searchable.stream()
        .collect(Collectors.toMap(PropertySchema::getName, Function.identity(), (a, b) -> a));
  }

  @Test
  @DisplayName("The text search matches the inject, simulation and scenario of an occurrence")
  void searchMatchesOccurrenceContext() {
    Map<String, PropertySchema> searchable = searchableByName();

    assertThat(searchable.get("occurrences").getPaths())
        .containsExactlyInAnyOrder(
            "occurrences.inject.title",
            "occurrences.inject.exercise.name",
            "occurrences.inject.exercise.scenario.name");
  }

  @Test
  @DisplayName("The occurrence context is search-only: never filterable nor sortable")
  void occurrenceContextIsSearchOnly() {
    PropertySchema occurrences = searchableByName().get("occurrences");

    assertThat(occurrences.isFilterable()).isFalse();
    assertThat(occurrences.isSortable()).isFalse();
  }

  @Test
  @DisplayName("The text search keeps its existing fields and never reads the cleartext value")
  void searchKeepsExistingFieldsAndNeverTheCleartextValue() {
    Map<String, PropertySchema> searchable = searchableByName();

    assertThat(searchable).containsKeys("getFields", "valueDisplay", "name");
    assertThat(searchable.get("getFields").getPath()).isEqualTo("occurrences.field");
    // ADR-017: the cleartext value is never searchable.
    assertThat(searchable).doesNotContainKey("value");
  }
}
