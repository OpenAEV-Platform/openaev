package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAccessAnalysisResult extends OcsfObject {
  /**
   * The generalized access level or permission scope granted to the identity through the analyzed
   * policy configuration. Common examples include Read, Write, List, Delete, Admin, or custom
   * permission levels.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT accessLevelField;

  /**
   * The type or category of access being granted to the identity. This describes the nature of the
   * access relationship, such as cross-account access, public access, federated access, or
   * third-party integration access. Examples include 'Cross-Account', 'Public', 'Federated',
   * 'Service-to-Service', etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT accessTypeField;

  /**
   * The identities that are granted access through the analyzed policy configuration. This
   * identifies the specific entity that can exercise the permissions and helps assess the access
   * relationship and potential security implications. Examples include user accounts, service
   * principals, roles, account identifiers, or system identities.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessors")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectUser> accessorsField;

  /**
   * Details about supplementary restrictions and guardrails that may limit the granted access,
   * applied through additional policy types such as Resource Control Policies (RCPs) and Service
   * Control Policies (SCPs) in AWS, or other policy constraints.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "additional_restrictions")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAdditionalRestriction>
      additionalRestrictionsField;

  /**
   * The condition keys and their values that constrain when and how the granted access can be
   * exercised. These conditions define the circumstances under which the access relationship is
   * valid and the privileges can be used. Examples: IP address restrictions like
   * 'aws:SourceIp:192.0.2.0/24', time-based constraints like 'aws:RequestedRegion:us-east-1', MFA
   * requirements like 'aws:MultiFactorAuthPresent:true', or custom conditions based on resource
   * tags and request context.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "condition_keys")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject>
      conditionKeysField;

  /**
   * The specific privileges, actions, or permissions that are granted through the analyzed access
   * relationship. This includes the actual operations that the accessor can perform on the target
   * resource. Examples: AWS actions like 'sts:AssumeRole', 's3:GetObject', 'ec2:DescribeInstances';
   * Azure actions like 'Microsoft.Storage/storageAccounts/read'; or GCP permissions like
   * 'storage.objects.get'.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "granted_privileges")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      grantedPrivilegesField;
}
