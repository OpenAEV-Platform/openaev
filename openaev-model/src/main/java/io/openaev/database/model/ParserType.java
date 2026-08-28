package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ParserType {
  REGEX;

  @JsonCreator
  public static ParserType fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (ParserType type : values()) {
      if (type.name().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException(
        "output_parser_type must be REGEX; finding types like credentials belong in"
            + " contract_output_element_type, not output_parser_type. Got: "
            + value);
  }
}
