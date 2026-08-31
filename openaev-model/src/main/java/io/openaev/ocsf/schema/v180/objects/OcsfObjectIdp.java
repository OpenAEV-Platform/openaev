package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectIdp extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "auth_factors")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthFactor> authFactorsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint fingerprintField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "has_mfa")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT hasMfaField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "issuer")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT issuerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "protocol_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT protocolNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "scim")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectScim scimField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sso")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSso ssoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT stateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT stateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tenant_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT tenantUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT urlStringField;
}
