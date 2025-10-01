package io.openaev.rest.exercise.exports;

import static io.openaev.rest.exercise.exports.VariableMixin.*;
import static io.openaev.rest.exercise.exports.VariableWithValueMixin.VARIABLE_VALUE;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Variable;
import jakarta.validation.constraints.NotNull;

@JsonIncludeProperties(
    value = {
      VARIABLE_ID,
      VARIABLE_KEY,
      VARIABLE_VALUE,
      VARIABLE_DESCRIPTION,
    })
public abstract class VariableWithValueMixin {

  static final String VARIABLE_VALUE = "variable_value";

  public static String getId(@NotNull final JsonNode node) {
    return node.get(VariableMixin.VARIABLE_ID).textValue();
  }

  public static Variable build(@NotNull final JsonNode node) {
    Variable variable = new Variable();
    variable.setKey(node.get(VariableMixin.VARIABLE_KEY).asText());
    if (node.get(VARIABLE_VALUE) != null) {
      variable.setValue(node.get(VARIABLE_VALUE).asText());
    }
    variable.setDescription(node.get(VariableMixin.VARIABLE_DESCRIPTION).asText());
    return variable;
  }
}
