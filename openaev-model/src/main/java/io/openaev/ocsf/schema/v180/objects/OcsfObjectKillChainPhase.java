package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectKillChainPhase extends OcsfObject {
  /** The cyber kill chain phase. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phase")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT phaseField;

  /** The cyber kill chain phase identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phase_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT phaseIdField;
}
