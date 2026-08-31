package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSensorInfo extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sensor_layer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sensorLayerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sensor_layer_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT sensorLayerIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
