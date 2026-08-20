package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectCompliance {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "compliance_references")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKbArticle complianceReferencesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "standards")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT standardsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "requirements")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT requirementsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "control_parameters")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject controlParametersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "control")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT controlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "checks")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCheck checksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "compliance_standards")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKbArticle complianceStandardsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusCodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "assessments")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAssessment assessmentsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusDetailField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_details")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusDetailsField;
}
