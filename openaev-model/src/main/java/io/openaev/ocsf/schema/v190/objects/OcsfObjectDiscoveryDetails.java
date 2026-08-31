package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDiscoveryDetails extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT countField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "occurrence_details")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails occurrenceDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "occurrences")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails>
      occurrencesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRule ruleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;
}
