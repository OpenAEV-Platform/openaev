package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectKillChainPhase extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phase")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT phaseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "phase_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT phaseIdField;
}
