package io.openaev.schema;

import static lombok.AccessLevel.NONE;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.Filters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

@Builder
@Getter
public class PropertySchema {

  @NotBlank private final String name;

  @Getter(NONE)
  private final String jsonName;

  @NotNull private final Class<?> type;
  private final Type subtype;

  private final boolean unicity;
  private final boolean mandatory;
  private final boolean multiple;

  private final boolean searchable;
  private final boolean filterable;
  private final List<String> availableValues;
  private final boolean dynamicValues;
  private final boolean sortable;
  private final List<Filters.FilterOperator> overrideOperators;

  private final JoinTable joinTable;
  private final String path;
  private final String label;
  private final String entity;
  private final String[] paths;
  private final boolean keyword;

  @Singular("propertySchema")
  private final List<PropertySchema> propertiesSchema;

  public String getJsonName() {
    return Optional.ofNullable(this.jsonName).orElse(this.name);
  }

  @Builder
  @Getter
  public static class JoinTable {
    private final String joinOn;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    PropertySchema that = (PropertySchema) o;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  // -- VALIDATION --

  public static PropertySchemaBuilder builder() {
    return new ValidationBuilder();
  }

  private static class ValidationBuilder extends PropertySchemaBuilder {

    public PropertySchema build() {
      if (!hasText(super.name)) {
        throw new RuntimeException("Property name should not be empty");
      }
      if (super.type == null) {
        throw new RuntimeException("Property type should not be empty");
      }

      return super.build();
    }
  }
}
