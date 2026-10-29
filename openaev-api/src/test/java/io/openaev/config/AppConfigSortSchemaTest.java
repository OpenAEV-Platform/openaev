package io.openaev.config;

import static io.openaev.config.AppConfig.SORT_OBJECT_REF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppConfigSortSchemaTest {

  private static Schema<?> sortOf(OpenAPI openApi, String schemaName) {
    Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
    return (Schema<?>) schema.getProperties().get("sort");
  }

  @Test
  @DisplayName("a sort property generated as SortObject[] is pinned to a single SortObject")
  void given_sortObjectArray_should_pinToSingleSortObject() {
    // Arrange — the shape springdoc sometimes produced for paginated responses.
    Schema<?> page =
        new ObjectSchema()
            .addProperty("sort", new ArraySchema().items(new Schema<>().$ref(SORT_OBJECT_REF)));
    OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("PageThing", page));

    // Act
    AppConfig.pinSortObjectProperties(openApi);

    // Assert
    Schema<?> sort = sortOf(openApi, "PageThing");
    assertThat(sort.get$ref()).isEqualTo(SORT_OBJECT_REF);
    assertThat(sort.getItems()).isNull();
  }

  @Test
  @DisplayName("sort properties that are already a SortObject or another array are left as is")
  void given_otherSortShapes_should_beLeftUnchanged() {
    // Arrange
    Schema<?> pageable =
        new ObjectSchema().addProperty("sort", new Schema<>().$ref(SORT_OBJECT_REF));
    Schema<?> search =
        new ObjectSchema().addProperty("sort", new ArraySchema().items(new StringSchema()));
    OpenAPI openApi =
        new OpenAPI()
            .components(
                new Components()
                    .addSchemas("PageableObject", pageable)
                    .addSchemas("SearchInput", search)
                    .addSchemas("NoProperties", new StringSchema()));

    // Act
    AppConfig.pinSortObjectProperties(openApi);

    // Assert
    assertThat(sortOf(openApi, "PageableObject").get$ref()).isEqualTo(SORT_OBJECT_REF);
    assertThat(sortOf(openApi, "SearchInput")).isInstanceOf(ArraySchema.class);
  }

  @Test
  @DisplayName("an OpenAPI document without components is ignored")
  void given_noComponents_should_doNothing() {
    // Act & Assert
    assertThatCode(() -> AppConfig.pinSortObjectProperties(new OpenAPI()))
        .doesNotThrowAnyException();
  }
}
