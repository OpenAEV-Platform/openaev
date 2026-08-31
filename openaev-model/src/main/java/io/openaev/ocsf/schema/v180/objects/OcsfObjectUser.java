package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectUser extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "account")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectAccount accountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "credential_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT credentialUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "display_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT displayNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT emailAddrField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "forward_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT forwardAddrField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "full_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT fullNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "groups")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup> groupsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "has_mfa")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT hasMfaField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ldap_person")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLdapPerson ldapPersonField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOrganization orgField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT phoneNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "programmatic_credentials")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectProgrammaticCredential>
      programmaticCredentialsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
