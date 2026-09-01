package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAnomaly extends OcsfObject {
  /**
   * The specific parameter, metric or property where the anomaly was observed. Examples include:
   * CPU usage percentage, API response time in milliseconds, HTTP error rate, memory utilization,
   * network latency, transaction volume, etc. This helps identify the exact aspect of the system
   * exhibiting anomalous behavior.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_parameter")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT observationParameterField;

  /**
   * The type of analysis methodology used to detect the anomaly. This indicates how the anomaly was
   * identified through different analytical approaches. Common types include: Frequency Analysis,
   * Time Pattern Analysis, Volume Analysis, Sequence Analysis, Distribution Analysis, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT observationTypeField;

  /**
   * Details about the observed anomaly or observations that were flagged as anomalous compared to
   * expected baseline behavior.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectObservation>
      observationsField;

  /**
   * The specific pattern identified within the observation type. For Frequency Analysis, this could
   * be 'FREQUENT', 'INFREQUENT', 'RARE', or 'UNSEEN'. For Time Pattern Analysis, this could be
   * 'BUSINESS_HOURS', 'OFF_HOURS', or 'UNUSUAL_TIME'. For Volume Analysis, this could be
   * 'NORMAL_VOLUME', 'HIGH_VOLUME', or 'SURGE'. The pattern values are specific to each observation
   * type and indicate how the observed behavior relates to the baseline.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observed_pattern")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT observedPatternField;
}
