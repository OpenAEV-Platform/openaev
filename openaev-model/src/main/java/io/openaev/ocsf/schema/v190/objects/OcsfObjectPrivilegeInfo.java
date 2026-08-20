package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectPrivilegeInfo {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastUsedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_unused")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isUnusedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastUsedTimeDtField;
}
