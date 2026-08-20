package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectJobTrigger {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "event_codes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT eventCodesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastRunTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser userField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_run_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastRunTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_sources")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logSourcesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT nextRunTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "properties")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject propertiesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "next_run_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT nextRunTimeField;
}
