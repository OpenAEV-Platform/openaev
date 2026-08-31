package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectStartupItem extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "driver")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectKernelDriver driverField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "job")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectJob jobField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess processField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_mode_ids")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT>
      runModeIdsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_modes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> runModesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT runStateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT runStateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT startTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT startTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "win_service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectWinService winServiceField;
}
