package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAuthorization extends OcsfObject {
  /** Authorization Result/outcome, e.g. allowed, denied. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "decision")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT decisionField;

  /** Details about the Identity/Access management policies that are applicable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;
}
