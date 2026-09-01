package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectHassh extends OcsfObject {
  /**
   * The concatenation of key exchange, encryption, authentication and compression algorithms
   * (separated by ';'). NOTE: This is not the underlying algorithm for the hash implementation.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT algorithmField;

  /** The hash of the key exchange, encryption, authentication and compression algorithms. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint fingerprintField;
}
