package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSession extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "credential_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT credentialUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_reason")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT expirationReasonField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_mfa")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isMfaField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_remote")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isRemoteField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_vpn")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isVpnField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "issuer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT issuerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "terminal")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT terminalField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT uuidField;
}
