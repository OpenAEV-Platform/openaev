package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectEpss extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "percentile")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeFloatT percentileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT scoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;
}
