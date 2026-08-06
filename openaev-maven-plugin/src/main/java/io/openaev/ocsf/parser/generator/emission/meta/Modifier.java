package io.openaev.ocsf.parser.generator.emission.meta;

import lombok.Getter;

public enum Modifier {
  PUBLIC("public"),
  PROTECTED("protected"),
  PRIVATE("private");

  @Getter private final String value;

  Modifier(String value) {
    this.value = value;
  }
}
