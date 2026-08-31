package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectJa4Fingerprint extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_a")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionAField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_b")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionBField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_c")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionCField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_d")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionDField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;
}
