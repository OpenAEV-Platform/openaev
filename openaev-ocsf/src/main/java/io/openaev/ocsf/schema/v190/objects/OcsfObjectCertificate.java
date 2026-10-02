package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCertificate extends OcsfObject {
  /** The time when the certificate was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the certificate was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The expiration time of the certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  /** The expiration time of the certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  /** The fingerprint list of the certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprints")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint>
      fingerprintsField;

  /**
   * Denotes whether a digital certificate is self-signed or signed by a known certificate authority
   * (CA).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_self_signed")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSelfSignedField;

  /** The certificate issuer distinguished name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "issuer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT issuerField;

  /** The list of subject alternative names that are secured by a specific certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sans")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectSan> sansField;

  /** The serial number of the certificate used to create the digital signature. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "serial_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serialNumberField;

  /** The certificate subject distinguished name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subject")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT subjectField;

  /** The unique identifier of the certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /** The certificate version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
