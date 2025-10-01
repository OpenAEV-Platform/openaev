package io.openaev.rest.exercise.exports;

import static io.openaev.rest.exercise.exports.VariableMixin.*;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

@JsonIncludeProperties(
    value = {
      VARIABLE_ID,
      VARIABLE_KEY,
      VARIABLE_DESCRIPTION,
    })
public abstract class VariableMixin {

  static final String VARIABLE_ID = "variable_id";
  static final String VARIABLE_KEY = "variable_key";
  static final String VARIABLE_DESCRIPTION = "variable_description";
}
