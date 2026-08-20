package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectAccessAnalysisResult {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "granted_privileges")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT grantedPrivilegesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT accessLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "condition_keys")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject conditionKeysField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessors")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser accessorsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "additional_restrictions")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAdditionalRestriction
      additionalRestrictionsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT accessTypeField;
}
