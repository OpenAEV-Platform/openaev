package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectIdp extends OcsfObject {
  /**
   * The Authentication Factors object describes the different types of Multi-Factor Authentication
   * (MFA) methods and/or devices supported by the Identity Provider.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_factors")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthFactor> authFactorsField;

  /** The primary domain associated with the Identity Provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  /** The fingerprint of the X.509 certificate used by the Identity Provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint fingerprintField;

  /** The Identity Provider enforces Multi Factor Authentication (MFA). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "has_mfa")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT hasMfaField;

  /** The unique identifier (often a URL) used by the Identity Provider as its issuer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "issuer")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT issuerField;

  /** The name of the Identity Provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The supported protocol of the Identity Provider. E.g., <code>SAML</code>, <code>OIDC</code>, or
   * <code>OAuth2</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT protocolNameField;

  /**
   * The System for Cross-domain Identity Management (SCIM) resource object provides a structured
   * set of attributes related to SCIM protocols used for identity provisioning and management
   * across cloud-based platforms. It standardizes user and group provisioning details, enabling
   * identity synchronization and lifecycle management with compatible Identity Providers (IdPs) and
   * applications. SCIM is defined in <a target='_blank'
   * href='https://datatracker.ietf.org/doc/html/rfc7643'>RFC-7634</a>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "scim")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectScim scimField;

  /**
   * The Single Sign-On (SSO) object provides a structure for normalizing SSO attributes,
   * configuration, and/or settings from Identity Providers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sso")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSso ssoField;

  /**
   * The configuration state of the Identity Provider, normalized to the caption of the <code>
   * state_id</code> value. In the case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT stateField;

  /**
   * The normalized state ID of the Identity Provider to reflect its configuration or activation
   * status.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT stateIdField;

  /** The tenant ID associated with the Identity Provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tenant_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT tenantUidField;

  /** The unique identifier of the Identity Provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The URL for accessing the configuration or metadata of the Identity Provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT urlStringField;
}
