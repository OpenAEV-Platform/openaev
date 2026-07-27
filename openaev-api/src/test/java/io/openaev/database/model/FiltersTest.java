package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Filters.FilterMode;
import io.openaev.database.model.Filters.FilterOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Filters enum deserialization")
class FiltersTest {

  @Nested
  @DisplayName("FilterMode.fromValue")
  class FilterModeFromValue {

    @Test
    @DisplayName("lowercase value resolves normally")
    void given_lowercase_should_resolve() {
      assertThat(FilterMode.fromValue("and")).isEqualTo(FilterMode.and);
      assertThat(FilterMode.fromValue("or")).isEqualTo(FilterMode.or);
    }

    @Test
    @DisplayName("uppercase value resolves case-insensitively")
    void given_uppercase_should_resolveCaseInsensitively() {
      assertThat(FilterMode.fromValue("AND")).isEqualTo(FilterMode.and);
      assertThat(FilterMode.fromValue("OR")).isEqualTo(FilterMode.or);
    }

    @Test
    @DisplayName("mixed case resolves case-insensitively")
    void given_mixedCase_should_resolveCaseInsensitively() {
      assertThat(FilterMode.fromValue("And")).isEqualTo(FilterMode.and);
    }

    @Test
    @DisplayName("null falls back to 'and'")
    void given_null_should_fallbackToAnd() {
      assertThat(FilterMode.fromValue(null)).isEqualTo(FilterMode.and);
    }

    @Test
    @DisplayName("unknown value falls back to 'and'")
    void given_unknownValue_should_fallbackToAnd() {
      assertThat(FilterMode.fromValue("UNKNOWN")).isEqualTo(FilterMode.and);
    }
  }

  @Nested
  @DisplayName("FilterOperator.fromValue")
  class FilterOperatorFromValue {

    @Test
    @DisplayName("lowercase value resolves normally")
    void given_lowercase_should_resolve() {
      assertThat(FilterOperator.fromValue("contains")).isEqualTo(FilterOperator.contains);
      assertThat(FilterOperator.fromValue("eq")).isEqualTo(FilterOperator.eq);
      assertThat(FilterOperator.fromValue("not_eq")).isEqualTo(FilterOperator.not_eq);
    }

    @Test
    @DisplayName("uppercase value resolves case-insensitively")
    void given_uppercase_should_resolveCaseInsensitively() {
      assertThat(FilterOperator.fromValue("CONTAINS")).isEqualTo(FilterOperator.contains);
      assertThat(FilterOperator.fromValue("EQ")).isEqualTo(FilterOperator.eq);
      assertThat(FilterOperator.fromValue("NOT_EQ")).isEqualTo(FilterOperator.not_eq);
      assertThat(FilterOperator.fromValue("STARTS_WITH")).isEqualTo(FilterOperator.starts_with);
    }

    @Test
    @DisplayName("null falls back to 'eq'")
    void given_null_should_fallbackToEq() {
      assertThat(FilterOperator.fromValue(null)).isEqualTo(FilterOperator.eq);
    }

    @Test
    @DisplayName("unknown value falls back to 'eq'")
    void given_unknownValue_should_fallbackToEq() {
      assertThat(FilterOperator.fromValue("UNKNOWN_OP")).isEqualTo(FilterOperator.eq);
    }
  }

  @Nested
  @DisplayName("Jackson deserialization via ObjectMapper")
  class JacksonDeserialization {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("uppercase operator in JSON deserializes without error")
    void given_uppercaseOperatorInJson_should_deserializeWithoutError() throws Exception {
      // GIVEN - body as posted by an older injector (CONTAINS instead of contains)
      String json =
          """
          {
            "mode": "or",
            "filters": [
              {
                "id": "test-id",
                "key": "assetGroups",
                "mode": "or",
                "operator": "CONTAINS",
                "values": ["group-id"]
              }
            ]
          }
          """;

      // WHEN
      Filters.FilterGroup filterGroup = mapper.readValue(json, Filters.FilterGroup.class);

      // THEN
      assertThat(filterGroup.getMode()).isEqualTo(FilterMode.or);
      assertThat(filterGroup.getFilters()).hasSize(1);
      assertThat(filterGroup.getFilters().get(0).getOperator()).isEqualTo(FilterOperator.contains);
    }

    @Test
    @DisplayName("uppercase mode in JSON deserializes without error")
    void given_uppercaseModeInJson_should_deserializeWithoutError() throws Exception {
      // GIVEN
      String json = "{\"mode\": \"OR\", \"filters\": []}";

      // WHEN
      Filters.FilterGroup filterGroup = mapper.readValue(json, Filters.FilterGroup.class);

      // THEN
      assertThat(filterGroup.getMode()).isEqualTo(FilterMode.or);
    }
  }
}
