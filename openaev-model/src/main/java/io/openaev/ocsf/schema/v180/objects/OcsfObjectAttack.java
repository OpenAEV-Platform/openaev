package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAttack extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mitigation")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMitigation mitigationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sub_technique")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSubTechnique subTechniqueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tactic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTactic tacticField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tactics")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectTactic> tacticsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "technique")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTechnique techniqueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
