package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectServicePrivilegeAnalysis extends OcsfObject {
  /** Indicates whether all privileges within this service are unused. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "all_privileges_unused")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT allPrivilegesUnusedField;

  /** The total count of privileges analyzed within this service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "analyzed_privileges_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT analyzedPrivilegesCountField;

  /** The count of execute-type privileges within this service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "execute_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT executeCountField;

  /** The most recent time any privilege in this service was used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastUsedTimeDtField;

  /** The most recent time any privilege in this service was used. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastUsedTimeField;

  /**
   * The service or namespace identifier. Examples: <code>s3</code>, <code>ec2</code> (AWS); <code>
   * Microsoft.Storage</code> (Azure); <code>storage</code> (GCP).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The list of privilege-to-attack mappings for this service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "privilege_attack_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeAttackInfo>
      privilegeAttackInfoListField;

  /** The count of read-type privileges within this service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "read_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT readCountField;

  /** The count of unused privileges within this service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "unused_privileges_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT unusedPrivilegesCountField;

  /** The count of write-type privileges within this service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "write_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT writeCountField;
}
