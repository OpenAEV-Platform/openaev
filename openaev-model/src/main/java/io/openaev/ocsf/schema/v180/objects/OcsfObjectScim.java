package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectScim extends OcsfObject {
  /**
   * The authorization protocol as defined by the caption of <code>auth_protocol_id</code>. In the
   * case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT authProtocolField;

  /** The normalized identifier of the authorization protocol used by the SCIM resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_protocol_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT authProtocolIdField;

  /** When the SCIM resource was added to the service provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** When the SCIM resource was added to the service provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** Message or code associated with the last encountered error. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error_message")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT errorMessageField;

  /**
   * Indicates whether the SCIM resource is configured to provision groups, automatically or
   * otherwise.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_group_provisioning_enabled")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT
      isGroupProvisioningEnabledField;

  /**
   * Indicates whether the SCIM resource is configured to provision users, automatically or
   * otherwise.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_user_provisioning_enabled")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isUserProvisioningEnabledField;

  /** Timestamp of the most recent successful synchronization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastRunTimeDtField;

  /** Timestamp of the most recent successful synchronization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastRunTimeField;

  /** The most recent time when the SCIM resource was updated at the service provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The most recent time when the SCIM resource was updated at the service provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The name of the SCIM resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The supported protocol for the SCIM resource. E.g., <code>SAML</code>, <code>OIDC</code>, or
   * <code>OAuth2</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT protocolNameField;

  /**
   * Maximum number of requests allowed by the SCIM resource within a specified time frame to avoid
   * throttling.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rate_limit")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT rateLimitField;

  /**
   * SCIM provides a schema for representing groups, identified using the following schema URI:
   * <code>urn:ietf:params:scim:schemas:core:2.0:Group</code> as defined in <a target='_blank'
   * href='https://datatracker.ietf.org/doc/html/rfc7643'>RFC-7634</a>. This attribute will capture
   * key-value pairs for the scheme implemented in a SCIM resource.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "scim_group_schema")
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT scimGroupSchemaField;

  /**
   * SCIM provides a resource type for user resources. The core schema for user is identified using
   * the following schema URI: <code>urn:ietf:params:scim:schemas:core:2.0:User</code> as defined in
   * <a target='_blank' href='https://datatracker.ietf.org/doc/html/rfc7643'>RFC-7634</a>. his
   * attribute will capture key-value pairs for the scheme implemented in a SCIM resource. This
   * object is inclusive of both the basic and Enterprise User Schema Extension.
   */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v180.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "scim_user_schema")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT scimUserSchemaField;

  /**
   * The provisioning state of the SCIM resource, normalized to the caption of the <code>state_id
   * </code> value. In the case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT stateField;

  /** The normalized state ID of the SCIM resource to reflect its activation status. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT stateIdField;

  /**
   * A String that is an identifier for the resource as defined by the provisioning client. The
   * <code>externalId</code> may simplify identification of a resource between the provisioning
   * client and the service provider by allowing the client to use a filter to locate the resource
   * with an identifier from the provisioning domain, obviating the need to store a local mapping
   * between the provisioning domain's identifier of the resource and the identifier used by the
   * service provider.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** A unique identifier for a SCIM resource as defined by the service provider. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The primary URL for SCIM API requests. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT urlStringField;

  /**
   * Name of the vendor or service provider implementing SCIM. E.g., <code>Okta</code>, <code>Auth0
   * </code>, <code>Microsoft</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vendorNameField;

  /** SCIM protocol version supported e.g., <code>SCIM 2.0</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
