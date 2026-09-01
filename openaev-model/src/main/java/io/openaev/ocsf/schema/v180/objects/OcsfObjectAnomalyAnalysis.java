package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAnomalyAnalysis extends OcsfObject {
  /**
   * The analysis targets define the scope of monitored activities, specifying what entities,
   * systems or processes are analyzed for activity patterns.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "analysis_targets")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalysisTarget>
      analysisTargetsField;

  /**
   * List of detected activities that significantly deviate from the established baselines. This can
   * include unusual access patterns, unexpected user-agents, abnormal API usage, suspicious traffic
   * spikes, unauthorized access attempts, and other activities that may indicate potential security
   * threats or system issues.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "anomalies")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAnomaly> anomaliesField;

  /**
   * List of established patterns representing normal activity that serve as reference points for
   * anomaly detection. This includes typical user interaction patterns like common user-agents,
   * expected API access frequencies and patterns, standard resource utilization levels, and regular
   * traffic flows. These baselines help establish what constitutes 'normal' activity in the system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "baselines")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectBaseline> baselinesField;
}
