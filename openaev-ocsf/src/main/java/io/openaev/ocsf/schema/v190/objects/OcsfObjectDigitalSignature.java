package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDigitalSignature extends OcsfObject {
  /**
   * The digital signature algorithm used to create the signature, normalized to the caption of
   * 'algorithm_id'. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  /** The identifier of the normalized digital signature algorithm. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  /** The certificate object containing information about the digital certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate certificateField;

  /** The time when the digital signature was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the digital signature was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The developer ID on the certificate that signed the file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "developer_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT developerUidField;

  /**
   * The message digest attribute contains the fixed length message hash representation and the
   * corresponding hashing algorithm information.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "digest")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint digestField;

  /**
   * The canonical serialization or signing-envelope scheme used to produce the deterministic byte
   * sequence that was signed, normalized to the caption of <code>serialization_id</code>. In the
   * case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serializationField;

  /**
   * The identifier of the normalized canonical serialization or signing-envelope scheme used to
   * produce the deterministic byte sequence that was signed. A verifier must apply the same scheme
   * to reproduce the signing input. Use <code>Flat</code> where the signed data is an opaque byte
   * sequence, such as file content, to which no canonical serialization was applied. Distinct from
   * <code>algorithm_id</code>, which identifies how the resulting bytes were signed; some signing
   * formats (e.g. <code>Authenticode</code>) define their own canonicalization and populate both
   * fields.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "serialization_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serializationIdField;

  /**
   * The digital signature state defines the signature state, normalized to the caption of
   * 'state_id'. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT stateField;

  /** The normalized identifier of the signature state. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT stateIdField;
}
