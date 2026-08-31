package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAttestation extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "authority_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT authorityUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "chain_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT chainUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint fingerprintField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "prev_event")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPrevEvent prevEventField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "signatures")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature>
      signaturesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
