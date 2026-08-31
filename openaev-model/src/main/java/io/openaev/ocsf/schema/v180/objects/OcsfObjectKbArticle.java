package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectKbArticle extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "avg_timespan")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTimespan avgTimespanField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bulletin")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT bulletinField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "classification")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT installStateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT installStateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_superseded")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSupersededField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOs osField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT titleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
