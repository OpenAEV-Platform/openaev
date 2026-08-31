package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectServicePrivilegeAnalysis extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "all_privileges_unused")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT allPrivilegesUnusedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "analyzed_privileges_count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT analyzedPrivilegesCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "execute_count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT executeCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastUsedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastUsedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "privilege_attack_info_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectPrivilegeAttackInfo>
      privilegeAttackInfoListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "read_count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT readCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "unused_privileges_count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT unusedPrivilegesCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "write_count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT writeCountField;
}
