package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAutonomousSystem extends OcsfObject {
  /** Organization name for the Autonomous System. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** Unique number that the AS is identified by. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT numberField;
}
