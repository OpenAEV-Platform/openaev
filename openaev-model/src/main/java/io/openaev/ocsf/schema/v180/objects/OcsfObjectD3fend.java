package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectD3fend extends OcsfObject {
  /**
   * The Tactic object describes the tactic ID and/or name that is associated with a countermeasure.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "d3f_tactic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTactic d3fTacticField;

  /**
   * The Technique object describes the technique ID and/or name associated with a countermeasure.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "d3f_technique")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTechnique d3fTechniqueField;

  /** The D3FEND™ Matrix version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
