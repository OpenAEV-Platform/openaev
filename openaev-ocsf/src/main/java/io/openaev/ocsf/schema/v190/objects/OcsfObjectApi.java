package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectApi extends OcsfObject {
  /** The information pertaining to the API group. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup groupField;

  /** Verb/Operation associated with the request */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "operation")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT operationField;

  /** Details pertaining to the API request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "request")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRequest requestField;

  /** Details pertaining to the API response. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "response")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectResponse responseField;

  /** The information pertaining to the API service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectService serviceField;

  /**
   * The API or client token used to authenticate or authorize the API request. This attribute
   * contains the base <code>token</code> object that represents: (1) IdP-issued client tokens
   * (type_id: 6) such as Okta API tokens or Microsoft Entra ID Application Registration client
   * secrets, or (2) generic API tokens/keys (type_id: 7) used for SaaS application authentication.
   * Use this attribute when the API request was authenticated using a token that should be tracked
   * as part of the API activity event. Note: Protocol-specific authentication tokens (Kerberos,
   * OIDC, SAML) should be represented using <code>authentication_token</code> in authentication
   * events, not in API activity events.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "token")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectToken tokenField;

  /** The version of the API service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
