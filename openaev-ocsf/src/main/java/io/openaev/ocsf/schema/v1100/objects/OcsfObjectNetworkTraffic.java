package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectNetworkTraffic extends OcsfObject {
  /**
   * The total number of bytes transferred in both directions (sum of <code>bytes_in</code> and
   * <code>bytes_out</code>). Counts bytes in Layer 3 (Network Layer) packets, including IP header
   * and payload.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT bytesField;

  /**
   * The number of bytes sent from the destination to the source. Counts bytes in Layer 3 (Network
   * Layer) packets, including IP header and payload.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_in")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT bytesInField;

  /**
   * The number of bytes that were missed during observation, typically due to packet loss or
   * sampling limitations. Counts bytes in Layer 3 (Network Layer) packets, including IP header and
   * payload.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_missed")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT bytesMissedField;

  /**
   * The number of bytes sent from the source to the destination. Counts bytes in Layer 3 (Network
   * Layer) packets, including IP header and payload.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bytes_out")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT bytesOutField;

  /**
   * The total number of chunks transferred in both directions (sum of chunks_in and chunks_out).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT chunksField;

  /** The number of chunks sent from the destination to the source (inbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks_in")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT chunksInField;

  /** The number of chunks sent from the source to the destination (outbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chunks_out")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT chunksOutField;

  /**
   * The total number of data bytes transferred in both directions (sum of <code>data_bytes_in
   * </code> and <code>data_bytes_out</code>). Counts the data carried by the Layer 4 (Transport
   * Layer) protocol, excluding the IP header and the transport header (such as TCP, UDP, etc.). Not
   * applicable to protocols carried directly over IP without a transport header, such as ICMP,
   * ICMPv6, IGMP, GRE, ESP and OSPF: leave it unset for those flows, whose bytes are counted by
   * <code>bytes</code>, <code>bytes_in</code> and <code>bytes_out</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_bytes")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT dataBytesField;

  /**
   * The number of data bytes sent from the destination to the source. Counts the data carried by
   * the Layer 4 (Transport Layer) protocol, excluding the IP header and the transport header (such
   * as TCP, UDP, etc.). Not applicable to protocols carried directly over IP without a transport
   * header, such as ICMP, ICMPv6, IGMP, GRE, ESP and OSPF: leave it unset for those flows, whose
   * bytes are counted by <code>bytes</code>, <code>bytes_in</code> and <code>bytes_out</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_bytes_in")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT dataBytesInField;

  /**
   * The number of data bytes sent from the source to the destination. Counts the data carried by
   * the Layer 4 (Transport Layer) protocol, excluding the IP header and the transport header (such
   * as TCP, UDP, etc.). Not applicable to protocols carried directly over IP without a transport
   * header, such as ICMP, ICMPv6, IGMP, GRE, ESP and OSPF: leave it unset for those flows, whose
   * bytes are counted by <code>bytes</code>, <code>bytes_in</code> and <code>bytes_out</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_bytes_out")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT dataBytesOutField;

  /** The end time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT endTimeDtField;

  /** The end time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT endTimeField;

  /**
   * The total number of packets transferred in both directions (sum of packets_in and packets_out).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT packetsField;

  /** The number of packets sent from the destination to the source (inbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets_in")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT packetsInField;

  /** The number of packets sent from the source to the destination (outbound direction). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packets_out")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT packetsOutField;

  /** The start time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time_dt")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeDatetimeT startTimeDtField;

  /** The start time of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_time")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeTimestampT startTimeField;

  /** The time span object representing the duration of the observation or reporting period. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "timespan")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectTimespan timespanField;
}
