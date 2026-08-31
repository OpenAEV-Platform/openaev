package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAccessAnalysisResult extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT accessLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT accessTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessors")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectUser> accessorsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "additional_restrictions")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAdditionalRestriction>
      additionalRestrictionsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "condition_keys")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject>
      conditionKeysField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "granted_privileges")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      grantedPrivilegesField;
}
