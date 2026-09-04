package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectParameter extends OcsfObject {
  /** The parameter name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The parameter value after function execution. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "post_value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT postValueField;

  /** The parameter value before function execution. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pre_value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT preValueField;
}
