package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectAnomalyAnalysis {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "anomalies")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAnomaly anomaliesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "analysis_targets")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalysisTarget analysisTargetsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "baselines")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectBaseline baselinesField;
}
