package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectKeyboardInfo extends OcsfObject {
  /** The number of function keys on client keyboard. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "function_keys")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT functionKeysField;

  /** The Input Method Editor (IME) file name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ime")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT imeField;

  /** The keyboard locale identifier name (e.g., en-US). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_layout")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyboardLayoutField;

  /** The keyboard numeric code. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_subtype")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT keyboardSubtypeField;

  /** The keyboard type (e.g., xt, ico). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT keyboardTypeField;
}
