package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectBaseline {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observations")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectObservation observationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observed_pattern")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observedPatternField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observationTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_parameter")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observationParameterField;
}
