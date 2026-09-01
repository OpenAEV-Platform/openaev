package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectObservation extends OcsfObject {
  /**
   * Integer representing the total number of times this specific value/event was observed across
   * all occurrences. Helps establish prevalence and patterns.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT countField;

  /**
   * The time window when the value or event was first observed. It is used to analyze activity
   * patterns, detect trends, or correlate events within a specific timeframe.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "timespan")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTimespan timespanField;

  /**
   * The specific value, event, indicator or data point that was observed and recorded. This is the
   * core piece of information being tracked.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;
}
