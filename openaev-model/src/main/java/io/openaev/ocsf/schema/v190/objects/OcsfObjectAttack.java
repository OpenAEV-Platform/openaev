package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectAttack {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tactic")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTactic tacticField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "technique")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTechnique techniqueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sub_technique")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectSubTechnique subTechniqueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mitigation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectMitigation mitigationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tactics")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTactic tacticsField;
}
