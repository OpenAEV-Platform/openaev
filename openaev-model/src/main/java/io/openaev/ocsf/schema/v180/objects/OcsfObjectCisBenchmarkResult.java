package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCisBenchmarkResult extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "remediation")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRemediation remediationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "rule")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRule ruleField;
}
