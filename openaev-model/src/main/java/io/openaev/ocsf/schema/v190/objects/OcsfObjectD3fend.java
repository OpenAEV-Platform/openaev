package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectD3fend extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "d3f_technique")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fTechnique d3fTechniqueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "d3f_tactic")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fTactic d3fTacticField;
}
