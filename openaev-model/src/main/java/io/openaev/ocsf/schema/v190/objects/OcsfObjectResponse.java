package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectResponse {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "containers")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer containersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT codeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT flagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "error_message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorMessageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageField;
}
