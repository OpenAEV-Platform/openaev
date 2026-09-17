package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.schema.SchemaUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Stable finding query schema")
class StableFindingSchemaTest {

  @Test
  @DisplayName("Exposes source type as a filter backed by the injector relation")
  void given_stableFindingSchema_should_exposeSourceTypeFilter() {
    // Arrange / Act
    var property =
        SchemaUtils.retrieveProperty(
            SchemaUtils.schema(StableFinding.class), "finding_source_type");

    // Assert
    assertThat(property.isFilterable()).isTrue();
    assertThat(property.getPath()).isEqualTo("sourceInjector.type");
  }
}
