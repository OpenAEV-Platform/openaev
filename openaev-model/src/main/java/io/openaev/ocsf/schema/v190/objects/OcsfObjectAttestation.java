package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectAttestation {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint fingerprintField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "chain_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT chainUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authority_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT authorityUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "prev_event")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPrevEvent prevEventField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "signatures")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature signaturesField;
}
