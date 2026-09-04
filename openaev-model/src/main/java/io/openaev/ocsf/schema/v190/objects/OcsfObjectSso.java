package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSso extends OcsfObject {
  /**
   * The authorization protocol as defined by the caption of <code>auth_protocol_id</code>. In the
   * case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT authProtocolField;

  /** The normalized identifier of the authentication protocol used by the SSO resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT authProtocolIdField;

  /** Digital Signature associated with the SSO resource, e.g., SAML X.509 certificate details. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "certificate")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate certificateField;

  /** When the SSO resource was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** When the SSO resource was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The duration (in minutes) for an SSO session, after which re-authentication is required. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration_mins")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT durationMinsField;

  /** Duration (in minutes) of allowed inactivity before Single Sign-On (SSO) session expiration. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "idle_timeout")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT idleTimeoutField;

  /** URL for initiating an SSO login request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "login_endpoint")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT loginEndpointField;

  /**
   * URL for initiating an SSO logout request, allowing sessions to be terminated across
   * applications.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "logout_endpoint")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT logoutEndpointField;

  /**
   * URL where metadata about the SSO configuration is available (e.g., for SAML configurations).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "metadata_endpoint")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT metadataEndpointField;

  /** The most recent time when the SSO resource was updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The most recent time when the SSO resource was updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The name of the SSO resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The supported protocol for the SSO resource. E.g., <code>SAML</code> or <code>OIDC</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT protocolNameField;

  /**
   * Scopes define the specific permissions or actions that the client is allowed to perform on
   * behalf of the user. Each scope represents a different set of permissions, and the user can
   * selectively grant or deny access to specific scopes during the authorization process.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "scopes")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> scopesField;

  /** A unique identifier for a SSO resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * Name of the vendor or service provider implementing SSO. E.g., <code>Okta</code>, <code>Auth0
   * </code>, <code>Microsoft</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;
}
