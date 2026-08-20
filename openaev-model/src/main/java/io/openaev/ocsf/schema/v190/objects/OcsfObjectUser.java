package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectUser {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOrganization orgField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "forward_addr")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT forwardAddrField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT emailAddrField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "credential_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT credentialUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "programmatic_credentials")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProgrammaticCredential
      programmaticCredentialsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "groups")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup groupsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "has_mfa")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT hasMfaField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ldap_person")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLdapPerson ldapPersonField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT phoneNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "full_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT fullNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "account")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAccount accountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "display_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT displayNameField;
}
