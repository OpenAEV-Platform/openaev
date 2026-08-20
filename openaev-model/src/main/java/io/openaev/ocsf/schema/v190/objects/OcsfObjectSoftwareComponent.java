package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectSoftwareComponent {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "author")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT authorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_component")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT relatedComponentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "license")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT licenseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "purl")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT purlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint hashField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "relationship")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT relationshipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "relationship_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT relationshipIdField;
}
