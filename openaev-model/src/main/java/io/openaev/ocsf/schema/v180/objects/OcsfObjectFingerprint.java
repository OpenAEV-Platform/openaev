package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFingerprint extends OcsfObject {
  /**
   * The algorithm or scheme used to create the fingerprint, normalized to the caption of <code>
   * algorithm_id</code>. In the case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT algorithmField;

  /**
   * The identifier of the normalized algorithm or scheme, which was used to create the fingerprint.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  /**
   * The fingerprint value.
   *
   * <p><b>Note:</b> This uses type <code>file_hash_t</code> (&quot;Hash&quot;), which has been
   * generalized for all fingerprints but retains the same name and caption for backwards
   * compatibility.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;
}
