package io.openaev.ocsf.parser.schema;

import lombok.Getter;

public enum OcsfSchemaVersion {
  _1_8_0(OcsfSchemaVersionString.STRING_1_8_0),
  _1_9_0(OcsfSchemaVersionString.STRING_1_9_0);

  @Getter private final String value;

  OcsfSchemaVersion(String value) {
    this.value = value;
  }

  public static OcsfSchemaVersion fromString(String string) {
    for (OcsfSchemaVersion version : OcsfSchemaVersion.values()) {
      if (version.getValue().equals(string)) {
        return version;
      }
    }
    throw new IllegalArgumentException("Not a supported OCSF schema version: %s".formatted(string));
  }
}
