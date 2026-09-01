package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectToken extends OcsfObject {
  /** The time that the token was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time that the token was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The expiration time of the token. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  /** The expiration time of the token. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  /** Indicates whether the token is renewable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_renewable")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isRenewableField;

  /** The last time the token was updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The last time the token was updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /**
   * The human-friendly name of a token or key, if available, such as the <code>name</code> from the
   * Okta API Token API.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The unique identifier of the tenant or organization that owns the token or key, or the tenant
   * context in which the token is authorized for use. This is particularly relevant in multi-tenant
   * Identity Provider scenarios where tokens are scoped to specific tenants.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tenant_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT tenantUidField;

  /**
   * The type of the token, normalized to the caption of the <code>type_id</code> value. This
   * indicates whether the token is a Client Token, API Token, or one of the protocol-specific token
   * types.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The normalized token type identifier. Valid values: 0 (Unknown), 1 (Ticket Granting Ticket -
   * Kerberos), 2 (Service Ticket - Kerberos), 3 (Identity Token - OIDC), 4 (Refresh Token - OIDC),
   * 5 (SAML Assertion), 6 (Client Token - IdP-issued), 7 (API Token - generic API keys), 99
   * (Other).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The unique ID of a token or key, if available, such as the <code>Secret ID</code> of Entra ID
   * Application Registration Client Secrets.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The network zone or geographic region that the token or key is authorized to be used from. This
   * may represent network-based access restrictions, geographic limitations, or other zone-based
   * authorization policies. Examples include Okta's network zone restrictions or cloud provider
   * region restrictions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT zoneField;
}
