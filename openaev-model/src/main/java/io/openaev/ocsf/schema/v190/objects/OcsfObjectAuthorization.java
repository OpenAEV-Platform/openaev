package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectAuthorization {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "decision")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT decisionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;
}
