package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectEncryptionDetails {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "key_length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT keyLengthField;
}
