package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDiscoveryDetails {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRule ruleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "occurrence_details")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails occurrenceDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "occurrences")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails occurrencesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;
}
