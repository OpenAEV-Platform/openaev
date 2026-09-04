package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectNetworkTraffic extends OcsfObject {
  /** The total number of bytes transferred in both directions (sum of bytes_in and bytes_out). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesField;

  /** The number of bytes sent from the destination to the source (inbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_in")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesInField;

  /**
   * The number of bytes that were missed during observation, typically due to packet loss or
   * sampling limitations.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_missed")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesMissedField;

  /** The number of bytes sent from the source to the destination (outbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_out")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT bytesOutField;

  /**
   * The total number of chunks transferred in both directions (sum of chunks_in and chunks_out).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT chunksField;

  /** The number of chunks sent from the destination to the source (inbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks_in")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT chunksInField;

  /** The number of chunks sent from the source to the destination (outbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks_out")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT chunksOutField;

  /** The end time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /** The end time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The total number of packets transferred in both directions (sum of packets_in and packets_out).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT packetsField;

  /** The number of packets sent from the destination to the source (inbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets_in")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT packetsInField;

  /** The number of packets sent from the source to the destination (outbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets_out")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT packetsOutField;

  /** The start time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /** The start time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT startTimeField;

  /** The time span object representing the duration of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "timespan")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan timespanField;
}
