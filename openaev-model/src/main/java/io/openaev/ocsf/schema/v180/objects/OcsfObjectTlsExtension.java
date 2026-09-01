package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTlsExtension extends OcsfObject {
  /** The data contains information specific to the particular extension type. */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  /** The TLS extension type. For example: <code>Server Name</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The TLS extension type identifier. See <a target='_blank'
   * href='https://datatracker.ietf.org/doc/html/rfc8446#page-35'>The Transport Layer Security (TLS)
   * extension page</a>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;
}
