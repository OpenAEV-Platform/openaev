package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPortInfo extends OcsfObject {
  /** The port number. For example: <code>80</code>, <code>443</code>, <code>22</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "port")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypePortT portField;

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
}
