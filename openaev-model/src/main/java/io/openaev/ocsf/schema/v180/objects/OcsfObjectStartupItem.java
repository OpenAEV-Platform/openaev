package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectStartupItem extends OcsfObject {
  /** The startup item kernel driver resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "driver")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectKernelDriver driverField;

  /** The startup item job resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "job")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectJob jobField;

  /** The unique name of the startup item. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The startup item process resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "process")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess processField;

  /**
   * The list of normalized identifiers that describe the startup items' properties when it is
   * running. Use this field to capture extended information about the process, which may depend on
   * the type of startup item. E.g., A Windows service that interacts with the desktop.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_mode_ids")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT>
      runModeIdsField;

  /**
   * The list of run_modes, normalized to the captions of the run_mode_id values. In the case of
   * 'Other', they are defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_modes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> runModesField;

  /** The run state of the startup item. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT runStateField;

  /** The run state ID of the startup item. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "run_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT runStateIdField;

  /** The start type of the startup item. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT startTypeField;

  /** The start type ID of the startup item. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT startTypeIdField;

  /** The startup item type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The startup item type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The startup item Windows service resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "win_service")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectWinService winServiceField;
}
