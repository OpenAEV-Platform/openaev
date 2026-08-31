package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCvss extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "base_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT baseScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "depth")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT depthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "metrics")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectMetric> metricsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "overall_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT overallScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vector_string")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vectorStringField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
