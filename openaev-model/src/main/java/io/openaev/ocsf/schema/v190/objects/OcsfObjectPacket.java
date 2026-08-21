package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectPacket extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "source")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sourceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "source_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT sourceIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_offset")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT startOffsetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "format_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT formatIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sequence_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT sequenceNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_offset")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT endOffsetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "format")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT formatField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT encodingIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT encodingField;
}
