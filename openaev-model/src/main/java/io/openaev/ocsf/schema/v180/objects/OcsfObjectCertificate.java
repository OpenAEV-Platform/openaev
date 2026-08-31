package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCertificate extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprints")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint>
      fingerprintsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_self_signed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSelfSignedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "issuer")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT issuerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sans")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectSan> sansField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "serial_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT serialNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subject")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subjectField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
