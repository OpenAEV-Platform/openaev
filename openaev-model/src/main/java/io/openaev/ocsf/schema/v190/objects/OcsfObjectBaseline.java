package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectBaseline extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_parameter")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observationParameterField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observationTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observations")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectObservation>
      observationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observed_pattern")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT observedPatternField;
}
