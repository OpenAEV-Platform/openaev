package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectStartupItem {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT startTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_mode_ids")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT runModeIdsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "driver")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKernelDriver driverField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT runStateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "job")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectJob jobField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT runStateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT startTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess processField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_modes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT runModesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "win_service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectWinService winServiceField;
}
