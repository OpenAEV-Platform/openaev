package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectPermissionAnalysisResult extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "total_potential_attacks_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT
      totalPotentialAttacksCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "condition_keys")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject conditionKeysField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "granted_privileges")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT grantedPrivilegesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "analyzed_privileges_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT analyzedPrivilegesCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_privilege_analysis_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectServicePrivilegeAnalysis
      servicePrivilegeAnalysisListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "unused_privileges_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT unusedPrivilegesCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "unused_services_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT unusedServicesCountField;
}
