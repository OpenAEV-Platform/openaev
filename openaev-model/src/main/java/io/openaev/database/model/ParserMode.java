package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ParserMode {
  STDOUT,
  STDERR,
  READ_FILE;

  @JsonCreator
  public static ParserMode fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (ParserMode mode : values()) {
      if (mode.name().equalsIgnoreCase(value)) {
        return mode;
      }
    }
    throw new IllegalArgumentException(
        "output_parser_mode must be STDOUT, STDERR, or READ_FILE. Got: " + value);
  }
}
