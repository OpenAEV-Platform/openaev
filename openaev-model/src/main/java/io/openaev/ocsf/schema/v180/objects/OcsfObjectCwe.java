package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCwe extends OcsfObject {
  /** The caption assigned to the Common Weakness Enumeration unique identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "caption")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT captionField;

  /**
   * URL pointing to the CWE Specification. For more information see <a target='_blank'
   * href='https://cwe.mitre.org/'>CWE.</a>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * The Common Weakness Enumeration unique number assigned to a specific weakness. A CWE Identifier
   * begins "CWE" followed by a sequence of digits that acts as a unique identifier. For example:
   * <code>CWE-123</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
