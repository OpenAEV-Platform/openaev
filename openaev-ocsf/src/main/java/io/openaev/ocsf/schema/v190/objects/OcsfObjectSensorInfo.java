package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSensorInfo extends OcsfObject {
  /** The name of the entity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The technology or sensor layer that emitted the data, normalized to the caption of <code>
   * sensor_layer_id</code>. When <code>sensor_layer_id</code> is <code>99</code> (Other), this
   * attribute must contain the source-specific label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sensor_layer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sensorLayerField;

  /**
   * The normalized identifier of the sensor layer that emitted the data. For example, use this to
   * classify the detection surface (e.g., IPS sensor, endpoint agent, email gateway). When the type
   * is not listed, use <code>99</code> (Other) and populate <code>sensor_layer</code> with the
   * source-specific label.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sensor_layer_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT sensorLayerIdField;

  /** The unique identifier of the entity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
