package io.openaev.schema.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Filters;
import io.openaev.schema.PropertySchema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PropertySchemaDTO {

  @NotBlank
  @JsonProperty("schema_property_name")
  private String jsonName;

  @NotNull
  @JsonProperty("schema_property_label")
  private String label;

  @NotNull
  @JsonProperty("schema_property_type")
  private String type;

  @NotNull
  @JsonProperty("schema_property_entity")
  private String entity;

  @JsonProperty("schema_property_type_array")
  private boolean isArray;

  @JsonProperty("schema_property_values")
  private List<String> values;

  @JsonProperty("schema_property_has_dynamic_value")
  private boolean dynamicValues;

  @JsonProperty("schema_property_override_operators")
  private List<Filters.FilterOperator> overrideOperators;

  public PropertySchemaDTO(@NotNull final PropertySchema propertySchema) {
    this.setJsonName(propertySchema.getJsonName());
    this.setEntity(propertySchema.getEntity());
    if (propertySchema.getLabel() != null) {
      this.setLabel(propertySchema.getLabel());
    } else {
      this.setLabel(propertySchema.getJsonName());
    }
    Class<?> clazzType = propertySchema.getType();
    this.setArray(clazzType.isArray() || Collection.class.isAssignableFrom(clazzType));
    this.setValues(propertySchema.getAvailableValues());
    this.setDynamicValues(propertySchema.isDynamicValues());
    this.setType(propertySchema.getType().getSimpleName().toLowerCase());
    this.setOverrideOperators(propertySchema.getOverrideOperators());
  }
}
