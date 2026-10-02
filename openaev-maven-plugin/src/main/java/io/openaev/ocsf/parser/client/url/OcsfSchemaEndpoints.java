package io.openaev.ocsf.parser.client.url;

import lombok.Getter;

public enum OcsfSchemaEndpoints {
  DATATYPES("/api/data_types"),
  DICTIONARY("/api/dictionary"),
  OBJECTS_INVENTORY("/api/objects"),
  OBJECT_SCHEMA("/api/objects/{0}"),
  CLASSES_INVENTORY("/api/classes"),
  CLASS_SCHEMA("/api/classes/{0}"),
  VERSIONS("/api/versions");

  @Getter private final String value;

  OcsfSchemaEndpoints(String value) {
    this.value = value;
  }
}
