package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEpss extends OcsfObject {
  /** The timestamp indicating when the EPSS score was calculated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The timestamp indicating when the EPSS score was calculated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /**
   * The EPSS score's percentile representing relative importance and ranking of the score in the
   * larger EPSS dataset.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "percentile")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT percentileField;

  /**
   * The EPSS score representing the probability [0-1] of exploitation in the wild in the next 30
   * days (following score publication).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT scoreField;

  /** The version of the EPSS model used to calculate the score. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
