package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPrivilegeAttackInfo extends OcsfObject {
  /** The MITRE ATT&CK technique that these privileges could enable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attack")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack attackField;

  /**
   * The list of privilege information objects, where each element describes a specific privilege
   * that could enable the associated attack technique.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "privilege_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeInfo>
      privilegeInfoListField;
}
