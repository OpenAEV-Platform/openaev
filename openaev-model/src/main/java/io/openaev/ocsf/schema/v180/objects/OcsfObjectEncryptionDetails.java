package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEncryptionDetails extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT algorithmField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_length")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT keyLengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT keyUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;
}
