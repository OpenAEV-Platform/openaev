package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFirewallRule extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT categoryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "condition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT conditionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "duration")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT durationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "match_details")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      matchDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "match_location")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT matchLocationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "rate_limit")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT rateLimitField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sensitivity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT sensitivityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
