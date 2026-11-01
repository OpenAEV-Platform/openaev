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

    assertThat(searchable.get("getSearchInjectTitle").getPath()).isEqualTo("inject.title");
    assertThat(searchable.get("getSearchSimulationName").getPath())
        .isEqualTo("inject.exercise.name");
    assertThat(searchable.get("getSearchScenarioName").getPath()).isEqualTo("inject.scenario.name");
  }

  @Test
  @DisplayName("The context properties are search-only: never filterable nor sortable")
  void contextPropertiesAreSearchOnly() {
    Map<String, PropertySchema> searchable = searchableByName();

    List.of("getSearchInjectTitle", "getSearchSimulationName", "getSearchScenarioName")
        .forEach(
            name -> {
              PropertySchema property = searchable.get(name);
              assertThat(property.isFilterable()).as(name).isFalse();
              assertThat(property.isSortable()).as(name).isFalse();
            });
  }

  @Test
  @DisplayName("The text search keeps its existing fields and never reads the cleartext value")
  void searchKeepsExistingFieldsAndNeverTheCleartextValue() {
    Map<String, PropertySchema> searchable = searchableByName();

    assertThat(searchable).containsKeys("field", "valueDisplay", "name");
    // ADR-017: the cleartext value is never searchable.
    assertThat(searchable).doesNotContainKey("value");
  }
}
