package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAuthorization extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "decision")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT decisionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;
}
