package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTimespan extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_days")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationDaysField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT durationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_hours")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationHoursField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_mins")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationMinsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_months")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationMonthsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_secs")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationSecsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_weeks")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationWeeksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_years")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationYearsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;
}
