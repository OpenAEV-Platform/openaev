package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectNetworkTraffic {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets_out")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT packetsOutField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_missed")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesMissedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks_out")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT chunksOutField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets_in")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT packetsInField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "timespan")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan timespanField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_in")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesInField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT chunksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT packetsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_out")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesOutField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks_in")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT chunksInField;
}
