package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAnomaly extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_parameter")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT observationParameterField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observation_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT observationTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observations")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectObservation>
      observationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "observed_pattern")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT observedPatternField;
}
