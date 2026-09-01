package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectJa4Fingerprint extends OcsfObject {
  /** The 'a' section of the JA4 fingerprint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_a")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionAField;

  /** The 'b' section of the JA4 fingerprint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_b")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionBField;

  /** The 'c' section of the JA4 fingerprint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_c")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionCField;

  /** The 'd' section of the JA4 fingerprint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "section_d")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sectionDField;

  /**
   * The JA4+ fingerprint type as defined by <a
   * href='https://blog.foxio.io/ja4+-network-fingerprinting target='_blank'>FoxIO</a>, normalized
   * to the caption of 'type_id'. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The identifier of the JA4+ fingerprint type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The JA4+ fingerprint value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;
}
