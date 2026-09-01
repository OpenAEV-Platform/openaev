package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDnsAnswer extends OcsfObject {
  /**
   * The class of DNS data contained in this resource record. See <a target='_blank'
   * href='https://www.rfc-editor.org/rfc/rfc1035.txt'>RFC1035</a>. For example: <code>IN</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classField;

  /** The list of DNS answer header flag IDs. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flag_ids")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT> flagIdsField;

  /** The list of DNS answer header flags. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> flagsField;

  /**
   * The DNS packet identifier assigned by the program that generated the query. The identifier is
   * copied to the response.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "packet_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT packetUidField;

  /**
   * The data describing the DNS resource. The meaning of this data depends on the type and class of
   * the resource record.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rdata")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rdataField;

  /**
   * The time interval that the resource record may be cached. Zero value means that the resource
   * record can only be used for the transaction in progress, and should not be cached.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ttl")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT ttlField;

  /**
   * The type of data contained in this resource record. See <a target='_blank'
   * href='https://www.rfc-editor.org/rfc/rfc1035.txt'>RFC1035</a>. For example: <code>CNAME</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;
}
