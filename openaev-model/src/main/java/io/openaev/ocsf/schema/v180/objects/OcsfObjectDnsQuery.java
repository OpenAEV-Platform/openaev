package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDnsQuery extends OcsfObject {
  /**
   * The class of resource records being queried. See <a target='_blank'
   * href='https://www.rfc-editor.org/rfc/rfc1035.txt'>RFC1035</a>. For example: <code>IN</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classField;

  /** The hostname or domain being queried. For example: <code>www.example.com</code> */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The DNS opcode specifies the type of the query message. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "opcode")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT opcodeField;

  /**
   * The DNS opcode ID specifies the normalized query message type as defined in <a target='_blank'
   * href='https://www.rfc-editor.org/rfc/rfc5395.html'>RFC-5395</a>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "opcode_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT opcodeIdField;

  /**
   * The DNS packet identifier assigned by the program that generated the query. The identifier is
   * copied to the response.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packet_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT packetUidField;

  /**
   * The type of resource records being queried. See <a target='_blank'
   * href='https://www.rfc-editor.org/rfc/rfc1035.txt'>RFC1035</a>. For example: A, AAAA, CNAME, MX,
   * and NS.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;
}
