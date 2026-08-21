package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectAnomalyAnalysis extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "anomalies")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAnomaly anomaliesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "baselines")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectBaseline baselinesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "analysis_targets")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalysisTarget analysisTargetsField;
}
