package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDigitalSignature extends OcsfObject {
  /**
   * The digital signature algorithm used to create the signature, normalized to the caption of
   * 'algorithm_id'. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT algorithmField;

  /** The identifier of the normalized digital signature algorithm. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT algorithmIdField;

  /** The certificate object containing information about the digital certificate. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectCertificate certificateField;

  /** The time when the digital signature was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the digital signature was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The developer ID on the certificate that signed the file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "developer_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT developerUidField;

  /**
   * The message digest attribute contains the fixed length message hash representation and the
   * corresponding hashing algorithm information.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "digest")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint digestField;

  /**
   * The digital signature state defines the signature state, normalized to the caption of
   * 'state_id'. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT stateField;

  /** The normalized identifier of the signature state. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT stateIdField;
}
