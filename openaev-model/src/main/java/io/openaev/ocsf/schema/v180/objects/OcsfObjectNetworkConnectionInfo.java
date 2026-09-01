package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectNetworkConnectionInfo extends OcsfObject {
  /**
   * The boundary of the connection, normalized to the caption of 'boundary_id'. In the case of
   * 'Other', it is defined by the event source.
   *
   * <p>For cloud connections, this translates to the traffic-boundary(same VPC, through IGW, etc.).
   * For traditional networks, this is described as Local, Internal, or External.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "boundary")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT boundaryField;

  /**
   * The normalized identifier of the boundary of the connection.
   *
   * <p>For cloud connections, this translates to the traffic-boundary (same VPC, through IGW,
   * etc.). For traditional networks, this is described as Local, Internal, or External.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "boundary_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT boundaryIdField;

  /** The Community ID of the network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "community_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT communityUidField;

  /**
   * The direction of the initiated connection, traffic, or email, normalized to the caption of the
   * direction_id value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "direction")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT directionField;

  /** The normalized identifier of the direction of the initiated connection, traffic, or email. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "direction_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT directionIdField;

  /**
   * The Connection Flag History summarizes events in a network connection. For example flags <code>
   *  ShAD </code> representing SYN, SYN/ACK, ACK and Data exchange.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flag_history")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT flagHistoryField;

  /**
   * The IP protocol name in lowercase, as defined by the Internet Assigned Numbers Authority
   * (IANA). For example: <code>tcp</code> or <code>udp</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT protocolNameField;

  /**
   * The IP protocol number, as defined by the Internet Assigned Numbers Authority (IANA). For
   * example: <code>6</code> for TCP and <code>17</code> for UDP.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_num")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT protocolNumField;

  /** The Internet Protocol version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_ver")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT protocolVerField;

  /** The Internet Protocol version identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_ver_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT protocolVerIdField;

  /** The authenticated user or service session. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSession sessionField;

  /** The network connection TCP header flags (i.e., control bits). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tcp_flags")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT tcpFlagsField;

  /** The unique identifier of the connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
