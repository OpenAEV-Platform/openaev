package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectPrivilegeAttackInfo {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attack")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack attackField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "privilege_info_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeInfo privilegeInfoListField;
}
