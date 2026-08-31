package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPrivilegeAttackInfo extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attack")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack attackField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "privilege_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeInfo>
      privilegeInfoListField;
}
