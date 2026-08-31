package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectD3fend extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "d3f_tactic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTactic d3fTacticField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "d3f_technique")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTechnique d3fTechniqueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
