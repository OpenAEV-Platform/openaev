package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPermissionAnalysisResult extends OcsfObject {
  /** The total count of privileges that were analyzed across all services. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "analyzed_privileges_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT analyzedPrivilegesCountField;

  /**
   * The condition keys and their values that were evaluated during policy analysis, including
   * contextual constraints that affect permission grants. These conditions define when and how
   * permissions are applied. Examples: <code>aws:SourceIp:1.2.3.4</code>, <code>
   * aws:RequestedRegion:us-east-1</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "condition_keys")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject>
      conditionKeysField;

  /**
   * The specific privileges, actions, or permissions that are explicitly granted by the analyzed
   * policy. Examples: AWS actions like <code>s3:GetObject</code>, <code>ec2:RunInstances</code>,
   * <code>iam:CreateUser</code>; Azure actions like <code>Microsoft.Storage/storageAccounts/read
   * </code>; or GCP permissions like <code>storage.objects.get</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "granted_privileges")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      grantedPrivilegesField;

  /**
   * Detailed information about the policy document that was analyzed, including policy metadata,
   * version, type (identity-based, resource-based, etc.), and structural details. This provides
   * context for understanding the scope and nature of the permission analysis.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  /** The list of privilege analysis results grouped by cloud service or namespace. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_privilege_analysis_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectServicePrivilegeAnalysis>
      servicePrivilegeAnalysisListField;

  /**
   * The total count of privilege-to-attack technique mappings identified across all analyzed
   * privileges.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "total_potential_attacks_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT
      totalPotentialAttacksCountField;

  /**
   * The total count of privileges or actions defined in the policy that have not been utilized
   * within the analysis timeframe. This metric helps identify over-privileged access and
   * opportunities for privilege reduction to follow the principle of least privilege. High counts
   * may indicate policy bloat or excessive permissions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "unused_privileges_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT unusedPrivilegesCountField;

  /**
   * The total count of cloud services or resource types referenced in the policy that have not been
   * accessed or utilized within the analysis timeframe. This helps identify unused service
   * permissions that could be removed to reduce attack surface. Examples: AWS services like S3,
   * SQS, IAM, Lambda; Azure services like Storage, Compute, KeyVault; or GCP services like Cloud
   * Storage, Compute Engine, BigQuery.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "unused_services_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT unusedServicesCountField;
}
