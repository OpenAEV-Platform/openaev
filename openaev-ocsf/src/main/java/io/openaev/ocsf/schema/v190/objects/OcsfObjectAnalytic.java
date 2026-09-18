package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAnalytic extends OcsfObject {
  /** The algorithm used by the underlying analytic to generate the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "algorithm")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT algorithmField;

  /** The analytic category. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryField;

  /** The description of the analytic that generated the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /** The name of the analytic that generated the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** Other analytics related to this analytic. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_analytics")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalytic>
      relatedAnalyticsField;

  /**
   * An array of <code>sensor_info</code> objects, each describing a sensor control point analyzed
   * or correlated by the analytic. Each element typically represents a source of <code>
   * related_events</code> that contributed to the finding. An individual sensor control point may
   * also directly originate the finding, though more commonly it emits alerts that a downstream
   * consumer treats as findings.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sensor_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectSensorInfo>
      sensorInfoListField;

  /** The Analytic state. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT stateField;

  /** The Analytic state identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT stateIdField;

  /** The analytic type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The analytic type ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The unique identifier of the analytic that generated the finding. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /** The analytic version. For example: <code>1.1</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
