package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAircraft extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation locationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT modelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "serial_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT serialNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "speed_accuracy")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT speedAccuracyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "speed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT speedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "track_direction")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT trackDirectionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vertical_speed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT verticalSpeedField;
}
