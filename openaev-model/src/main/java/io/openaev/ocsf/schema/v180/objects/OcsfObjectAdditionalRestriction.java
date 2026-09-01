package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAdditionalRestriction extends OcsfObject {
  /**
   * Detailed information about the policy document that defines this restriction, including policy
   * metadata, type, scope, and the specific rules or conditions that implement the access control.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy policyField;

  /**
   * The current status of the policy restriction, normalized to the caption of the <code>status_id
   * </code> enum value.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusField;

  /** The normalized status identifier indicating the applicability of this policy restriction. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT statusIdField;
}
