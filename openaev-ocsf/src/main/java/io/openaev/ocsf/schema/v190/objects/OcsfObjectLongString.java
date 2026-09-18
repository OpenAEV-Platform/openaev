package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectLongString extends OcsfObject {
  /**
   * Indicates that <code>value</code> has been truncated. May be omitted if truncation has not
   * occurred.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_truncated")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isTruncatedField;

  /**
   * The size in bytes of the string represented by <code>value</code> before truncation. Should be
   * omitted if truncation has not occurred.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "untruncated_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT untruncatedSizeField;

  /** The string value, truncated if <code>is_truncated</code> is <code>true</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;
}
