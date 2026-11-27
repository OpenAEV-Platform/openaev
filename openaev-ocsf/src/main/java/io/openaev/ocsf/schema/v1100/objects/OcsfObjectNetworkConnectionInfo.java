package io.openaev.ocsf.schema.v1100.objects;

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
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT boundaryField;

  /**
   * The normalized identifier of the boundary of the connection.
   *
   * <p>For cloud connections, this translates to the traffic-boundary (same VPC, through IGW,
   * etc.). For traditional networks, this is described as Local, Internal, or External.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "boundary_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT boundaryIdField;

  /** The Community ID of the network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "community_uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT communityUidField;

  /**
   * The direction of the initiated connection, traffic, or email, normalized to the caption of the
   * direction_id value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "direction")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT directionField;

  /** The normalized identifier of the direction of the initiated connection, traffic, or email. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "direction_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT directionIdField;

  /**
   * The Connection Flag History summarizes events in a network connection. For example flags <code>
   *  ShAD </code> representing SYN, SYN/ACK, ACK and Data exchange.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flag_history")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT flagHistoryField;

  /**
   * The control message code, which qualifies <code>icmp_type</code>. Applies to both ICMP and
   * ICMPv6 and, like <code>icmp_type</code>, is interpreted against the Internet Assigned Numbers
   * Authority (IANA) registry for the protocol in use (IP protocol number <code>1</code> for ICMP,
   * <code>58</code> for ICMPv6).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "icmp_code")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT icmpCodeField;

  /**
   * The control message type. Applies to both ICMP and ICMPv6, which the Internet Assigned Numbers
   * Authority (IANA) numbers in separate registries, so the same value can identify different
   * messages: interpret it against the registry for the protocol in use (IP protocol number <code>1
   * </code> for ICMP, <code>58</code> for ICMPv6). For example: <code>8</code> for an ICMP Echo
   * Request, or <code>128</code> for an ICMPv6 Echo Request.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "icmp_type")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT icmpTypeField;

  /**
   * The identifier carried in an ICMP query message or an ICMPv6 informational message, such as an
   * Echo Request or a Timestamp message, and repeated in the matching reply, used to correlate the
   * request with its reply.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "icmp_uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT icmpUidField;

  /**
   * The IP protocol name in lowercase, as defined by the Internet Assigned Numbers Authority
   * (IANA). For example: <code>tcp</code> or <code>udp</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT protocolNameField;

  /**
   * The IP protocol number, as defined by the Internet Assigned Numbers Authority (IANA). For
   * example: <code>6</code> for TCP and <code>17</code> for UDP.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_num")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT protocolNumField;

  /** The Internet Protocol version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_ver")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT protocolVerField;

  /** The Internet Protocol version identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_ver_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT protocolVerIdField;

  /** The authenticated user or service session. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectSession sessionField;

  /** The network connection TCP header flags (i.e., control bits). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tcp_flags")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT tcpFlagsField;

  /** The unique identifier of the connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;
}
