package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPrivilegeInfo extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_unused")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isUnusedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastUsedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_used_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastUsedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;
}
