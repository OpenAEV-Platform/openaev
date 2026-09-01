package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectKillChainPhase extends OcsfObject {
  /** The cyber kill chain phase. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phase")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT phaseField;

  /** The cyber kill chain phase identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phase_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT phaseIdField;
}
