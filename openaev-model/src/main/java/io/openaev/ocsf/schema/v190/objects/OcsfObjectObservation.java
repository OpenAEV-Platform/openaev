package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectObservation {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "timespan")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan timespanField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;
}
