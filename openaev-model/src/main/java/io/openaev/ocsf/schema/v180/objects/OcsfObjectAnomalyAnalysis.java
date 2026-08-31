package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAnomalyAnalysis extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "analysis_targets")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalysisTarget>
      analysisTargetsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "anomalies")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAnomaly> anomaliesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "baselines")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectBaseline> baselinesField;
}
