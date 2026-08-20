package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectImage {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tag")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT tagField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject tagsField;
}
