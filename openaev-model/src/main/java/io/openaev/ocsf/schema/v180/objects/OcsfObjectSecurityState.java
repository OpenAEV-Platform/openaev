package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSecurityState extends OcsfObject {
  /**
   * The security state, normalized to the caption of the state_id value. In the case of 'Other', it
   * is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT stateField;

  /** The security state of the managed entity. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT stateIdField;
}
