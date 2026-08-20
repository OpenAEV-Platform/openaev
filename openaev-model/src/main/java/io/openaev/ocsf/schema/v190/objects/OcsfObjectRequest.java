package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectRequest {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "containers")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer containersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT flagsField;
}
