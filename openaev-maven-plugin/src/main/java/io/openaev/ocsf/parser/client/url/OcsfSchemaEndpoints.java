package io.openaev.ocsf.parser.client.url;

import lombok.Getter;

public enum OcsfSchemaEndpoints {
  DATATYPES("/api/data_types"),
  DICTIONARY("/api/dictionary"),
  OBJECTS_INVENTORY("/api/objects"),
  OBJECT_SCHEMA("/schema/objects/{0}"),
  CLASSES_INVENTORY("/api/classes"),
  CLASS_SCHEMA("/schema/classes/{0}");

  @Getter private final String value;

  OcsfSchemaEndpoints(String value) {
    this.value = value;
  }
}
