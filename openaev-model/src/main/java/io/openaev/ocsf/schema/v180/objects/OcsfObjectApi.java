package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectApi extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "operation")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT operationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "request")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRequest requestField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "response")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectResponse responseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectService serviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "token")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectToken tokenField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
