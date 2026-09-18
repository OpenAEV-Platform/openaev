package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectKeyValueObject extends OcsfObject {
  /** The name of the key. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The value associated to the key. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT valueField;

  /**
   * Optional, the values associated to the key. You can populate this attribute, when you have
   * multiple values for the same key.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "values")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> valuesField;
}
