package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAuthenticationToken extends OcsfObject {
  /**
   * The time that the authentication token was created or issued. This corresponds to the token
   * issuance time, such as the <code>iat</code> (issued at) claim in OIDC tokens, the issue instant
   * in SAML assertions, or the ticket start time in Kerberos tickets.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /**
   * The time that the authentication token was created or issued. This corresponds to the token
   * issuance time, such as the <code>iat</code> (issued at) claim in OIDC tokens, the issue instant
   * in SAML assertions, or the ticket start time in Kerberos tickets.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The encryption details of the authentication token. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "encryption_details")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectEncryptionDetails encryptionDetailsField;

  /** The expiration time of the authentication token. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  /** The expiration time of the authentication token. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  /** Indicates whether the authentication token is renewable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_renewable")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isRenewableField;

  /**
   * A bitmask, either in hexadecimal or decimal form, which encodes various attributes or
   * permissions associated with a Kerberos ticket. These flags delineate specific characteristics
   * of the ticket, such as its renewability or forwardability.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kerberos_flags")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT kerberosFlagsField;

  /** The last time the token was updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The last time the token was updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /**
   * The human-friendly name of a token or key, if available, such as the <code>name</code> from the
   * Okta API Token API.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The unique identifier of the tenant or organization that owns the token or key, or the tenant
   * context in which the token is authorized for use. This is particularly relevant in multi-tenant
   * Identity Provider scenarios where tokens are scoped to specific tenants.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tenant_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT tenantUidField;

  /** The type of the authentication token. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The normalized authentication token type identifier. This attribute restricts the base <code>
   * token.type_id</code> enum to only protocol-specific authentication token types (values 0, 1-5,
   * 99). API tokens and client tokens (values 6-7) are not valid for <code>authentication_token
   * </code> - use the base <code>token</code> object for those types.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The unique ID of a token or key, if available, such as the <code>Secret ID</code> of Entra ID
   * Application Registration Client Secrets.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The network zone or geographic region that the token or key is authorized to be used from. This
   * may represent network-based access restrictions, geographic limitations, or other zone-based
   * authorization policies. Examples include Okta's network zone restrictions or cloud provider
   * region restrictions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT zoneField;
}
