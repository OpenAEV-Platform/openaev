package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectJob extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cmdLineField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastRunTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastRunTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT nextRunTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT nextRunTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT runStateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT runStateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser userField;
}
