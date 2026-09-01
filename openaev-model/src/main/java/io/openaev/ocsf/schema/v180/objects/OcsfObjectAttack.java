package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAttack extends OcsfObject {
  /**
   * The Mitigation object describes the MITRE ATT&CK® or ATLAS™ Mitigation ID and/or name that is
   * associated to an attack.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mitigation")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectMitigation mitigationField;

  /**
   * The Sub-technique object describes the MITRE ATT&CK® or ATLAS™ Sub-technique ID and/or name
   * associated to an attack.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sub_technique")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectSubTechnique subTechniqueField;

  /**
   * The Tactic object describes the MITRE ATT&CK® or ATLAS™ Tactic ID and/or name that is
   * associated to an attack.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tactic")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTactic tacticField;

  /**
   * The Tactic object describes the tactic ID and/or tactic name that are associated with the
   * attack technique, as defined by <a target='_blank'
   * href='https://attack.mitre.org/wiki/ATT&CK_Matrix'>ATT&CK® Matrix</a>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tactics")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectTactic> tacticsField;

  /**
   * The Technique object describes the MITRE ATT&CK® or ATLAS™ Technique ID and/or name associated
   * to an attack.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "technique")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTechnique techniqueField;

  /** The ATT&CK® or ATLAS™ Matrix version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
