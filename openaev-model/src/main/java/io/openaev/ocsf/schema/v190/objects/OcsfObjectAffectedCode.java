package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAffectedCode extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_column")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT endColumnField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_line")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT endLineField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser ownerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "remediation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRemediation remediationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "rule")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRule ruleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_column")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT startColumnField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_line")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT startLineField;
}
