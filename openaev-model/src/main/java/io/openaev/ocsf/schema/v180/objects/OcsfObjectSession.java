package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSession extends OcsfObject {
  /**
   * The number of identical sessions spawned from the same source IP, destination IP, application,
   * and content/threat type seen over a period of time.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT countField;

  /** The time when the session was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the session was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The unique identifier of the user's credential. For example, AWS Access Key ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "credential_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT credentialUidField;

  /** The reason which triggered the session expiration. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_reason")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT expirationReasonField;

  /** The session expiration time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  /** The session expiration time. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  /** Indicates whether Multi Factor Authentication was used during authentication. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_mfa")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isMfaField;

  /** The indication of whether the session is remote. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_remote")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isRemoteField;

  /** The indication of whether the session is a VPN session. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_vpn")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isVpnField;

  /** The identifier of the session issuer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "issuer")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT issuerField;

  /** The Pseudo Terminal associated with the session. Ex: the tty or pts value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "terminal")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT terminalField;

  /**
   * The alternate unique identifier of the session. e.g. AWS ARN - <code>
   * arn:aws:sts::123344444444:assumed-role/Admin/example-session</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier of the session. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The universally unique identifier of the session. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUuidT uuidField;
}
