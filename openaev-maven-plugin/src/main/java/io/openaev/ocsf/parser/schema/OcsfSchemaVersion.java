package io.openaev.ocsf.parser.schema;

import lombok.Getter;

public enum OcsfSchemaVersion {
  _1_8_0("1.8.0"),
  _1_9_0("1.9.0");

  @Getter private final String value;

  OcsfSchemaVersion(String value) {
    this.value = value;
  }
}
