package io.openaev.ocsf.parser.schema;

import lombok.Getter;

public enum OcsfSchemaVersion {
  _1_8("1.8.0");

  @Getter private final String value;

  OcsfSchemaVersion(String value) {
    this.value = value;
  }
}
