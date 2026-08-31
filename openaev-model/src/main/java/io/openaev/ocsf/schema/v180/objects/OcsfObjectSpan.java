package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSpan extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT durationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT endTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "operation")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT operationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT parentUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectService serviceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT startTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusCodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
