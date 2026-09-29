package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFingerprint extends OcsfObject {
  /**
   * The algorithm or scheme used to create the fingerprint, normalized to the caption of <code>
   * algorithm_id</code>. In the case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  /**
   * The identifier of the normalized algorithm or scheme, which was used to create the fingerprint.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  /**
   * The encoding of the <code>value</code> attribute, normalized to the caption of <code>
   * encoding_id</code>. In the case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT encodingField;

  /**
   * The normalized identifier of the encoding used to represent the fingerprint bytes as the string
   * in <code>value</code>. A verifier must decode <code>value</code> using this encoding to recover
   * the raw hash bytes.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "encoding_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT encodingIdField;

  /**
   * The canonical serialization scheme used to produce the deterministic byte sequence that was
   * fingerprinted, normalized to the caption of <code>serialization_id</code>. In the case of
   * <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serializationField;

  /**
   * The identifier of the normalized canonical serialization scheme used to produce the
   * deterministic byte sequence that was fingerprinted. A verifier must apply the same scheme to
   * reproduce the fingerprinted input. Use <code>Flat</code> where the fingerprinted data is an
   * opaque byte sequence, such as file content, to which no canonical serialization was applied.
   * Distinct from <code>algorithm_id</code>, which identifies how the resulting bytes were hashed.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serializationIdField;

  /**
   * The fingerprint value.
   *
   * <p><b>Note:</b> This uses type <code>file_hash_t</code> (&quot;Hash&quot;), which has been
   * generalized for all fingerprints but retains the same name and caption for backwards
   * compatibility.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;
}
