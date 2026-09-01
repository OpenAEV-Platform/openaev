package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectTimespan extends OcsfObject {
  /** The duration of the time span in days. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_days")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT durationDaysField;

  /** The duration of the time span in milliseconds. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT durationField;

  /** The duration of the time span in hours. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_hours")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT durationHoursField;

  /** The duration of the time span in minutes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_mins")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT durationMinsField;

  /** The duration of the time span in months. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_months")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT durationMonthsField;

  /** The duration of the time span in seconds. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_secs")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT durationSecsField;

  /** The duration of the time span in weeks. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_weeks")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT durationWeeksField;

  /** The duration of the time span in years. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_years")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT durationYearsField;

  /** The end time or conclusion of the timespan's interval. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /** The end time or conclusion of the timespan's interval. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT endTimeField;

  /** The start time or beginning of the timespan's interval. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /** The start time or beginning of the timespan's interval. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT startTimeField;

  /** The type of time span duration the object represents. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The normalized identifier for the time span duration type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;
}
