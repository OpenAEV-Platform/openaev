package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectIamRole extends OcsfObject {
  /**
   * The account associated with the role. For example, can be a cross-account role, different from
   * the user or group account, granting access within this account.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "account")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAccount accountField;

  /** The role name. For example, <code>Power Users</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** IAM policies associated with the role. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policies")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy> policiesField;

  /** List of privileges assigned to a role. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "privileges")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> privilegesField;

  /**
   * Programmatic credential (API keys, access tokens, certificates, etc) associated with the role.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "programmatic_credentials")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectProgrammaticCredential>
      programmaticCredentialsField;

  /** Resources that the role applies to. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resources")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectResourceDetails>
      resourcesField;

  /**
   * The session where the role is active. Some roles may be temporary and expire when the session
   * expires.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "session")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSession sessionField;

  /**
   * The alternate user identifier. For example, the Active Directory role GUID or the associated
   * LDAP group DN.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique role identifier. For example, the Windows role SID, or AWS role ARN. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
