package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectUser extends OcsfObject {
  /** The user's account or the account associated with the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "account")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectAccount accountField;

  /** The unique identifier of the user's credential. For example, AWS Access Key ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "credential_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT credentialUidField;

  /** The display name of the user, as reported by the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "display_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT displayNameField;

  /** The domain where the user is defined. For example: the LDAP or Active Directory domain. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  /** The user's primary email address. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT emailAddrField;

  /** The user's forwarding email address. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "forward_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT forwardAddrField;

  /** The full name of the user, as reported by the product. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "full_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT fullNameField;

  /** The administrative groups to which the user belongs. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "groups")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup> groupsField;

  /** The user has a multi-factor or secondary-factor device assigned. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "has_mfa")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT hasMfaField;

  /** The additional LDAP attributes that describe a person. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ldap_person")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLdapPerson ldapPersonField;

  /** The username. For example, <code>janedoe1</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** Organization and org unit related to the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOrganization orgField;

  /** The telephone number of the user. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT phoneNumberField;

  /**
   * Details about the programmatic credential (API keys, access tokens, certificates, etc)
   * associated to the user.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "programmatic_credentials")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectProgrammaticCredential>
      programmaticCredentialsField;

  /** The risk level, normalized to the caption of the risk_level_id value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskLevelField;

  /** The normalized risk level id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  /** The risk score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /** The type of the user. For example, System, AWS IAM User, etc. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The account type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The alternate user identifier. For example, the Active Directory user GUID or AWS user
   * Principal ID.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /**
   * The unique user identifier. For example, the Windows user SID, ActiveDirectory DN or AWS user
   * ARN.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
