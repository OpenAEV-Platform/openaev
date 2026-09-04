package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPacket extends OcsfObject {
  /**
   * The human-readable name of the encoding used to represent the packet data in the <code>value
   * </code> field. This should match the caption associated with <code>encoding_id</code>. If
   * <code>encoding_id</code> is 99 (Other), this field contains the original data source–specific
   * encoding value.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT encodingField;

  /**
   * The normalized identifier of the encoding method used to represent the packet data as a string.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT encodingIdField;

  /** The ending byte position of this packet within a capture file or stream. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_offset")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT endOffsetField;

  /**
   * The human-readable name of the packet capture file format in which the packet is stored. This
   * should match the caption associated with <code>format_id</code>. If <code>format_id</code> is
   * 99 (Other), this field contains the original data source–specific format value.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "format")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT formatField;

  /** The normalized identifier of the packet capture format. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "format_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT formatIdField;

  /**
   * The relative order number of this packet within its capture context (such as a PCAP file,
   * network session, or reconstructed stream). This represents chronological capture order,
   * distinct from both protocol-level sequencing (such as TCP sequence numbers).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sequence_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT sequenceNumberField;

  /**
   * The human-readable name describing how or where the packet was obtained. This should match the
   * caption associated with <code>source_id</code>. If <code>source_id</code> is 99 (Other), this
   * field contains the original data source–specific value.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "source")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sourceField;

  /** A normalized numeric identifier that specifies how the packet was obtained or generated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "source_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT sourceIdField;

  /** The starting byte position of this packet within a capture file or stream. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_offset")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT startOffsetField;

  /**
   * The actual packet data, represented as a string. The format of this string is determined by the
   * specified <code>encoding_id</code> (e.g., Base64, Hexadecimal, or URL Encoded).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;
}
