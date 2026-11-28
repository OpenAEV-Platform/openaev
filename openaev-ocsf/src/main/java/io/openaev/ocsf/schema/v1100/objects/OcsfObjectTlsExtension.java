package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTlsExtension extends OcsfObject {
  /** The data contains information specific to the particular extension type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v1100.JsonNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeJsonT dataField;

  /** The TLS extension type. For example: <code>Server Name</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT typeField;

  /** The TLS extension type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT typeIdField;
}
