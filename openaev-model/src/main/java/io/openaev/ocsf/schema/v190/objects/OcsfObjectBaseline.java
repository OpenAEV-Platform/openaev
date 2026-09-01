package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectBaseline extends OcsfObject {
  /**
   * The specific parameter or property being monitored. Examples include: CPU usage percentage, API
   * response time in milliseconds, HTTP error rate, memory utilization, network latency,
   * transaction volume, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_parameter")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observationParameterField;

  /**
   * The type of analysis being performed to establish baseline behavior. Common types include:
   * Frequency Analysis, Time Pattern Analysis, Volume Analysis, Sequence Analysis, Distribution
   * Analysis, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observationTypeField;

  /**
   * Collection of actual measured values, data points and observations recorded for this baseline.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observations")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectObservation>
      observationsField;

  /**
   * The specific pattern identified within the observation type. For Frequency Analysis, this could
   * be 'FREQUENT', 'INFREQUENT', 'RARE', or 'UNSEEN'. For Time Pattern Analysis, this could be
   * 'BUSINESS_HOURS', 'OFF_HOURS', or 'UNUSUAL_TIME'. For Volume Analysis, this could be
   * 'NORMAL_VOLUME', 'HIGH_VOLUME', or 'SURGE'. The pattern values are specific to each observation
   * type and indicate the baseline behavior.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observed_pattern")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observedPatternField;
}
