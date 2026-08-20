package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectKeyboardInfo {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_layout")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyboardLayoutField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_subtype")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT keyboardSubtypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyboardTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "function_keys")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT functionKeysField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ime")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT imeField;
}
