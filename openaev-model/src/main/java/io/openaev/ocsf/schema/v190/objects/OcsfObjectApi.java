package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectApi {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "response")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectResponse responseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "request")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRequest requestField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "operation")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT operationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup groupField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "token")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectToken tokenField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectService serviceField;
}
