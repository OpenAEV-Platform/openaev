package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectPrivilegeAttackInfo extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "privilege_info_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeInfo privilegeInfoListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attack")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack attackField;
}
