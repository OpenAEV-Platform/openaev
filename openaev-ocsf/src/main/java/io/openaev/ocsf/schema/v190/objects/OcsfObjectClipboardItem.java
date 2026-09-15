package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectClipboardItem extends OcsfObject {
  /**
   * The item's data in binary form, used when the clipboard content cannot be represented as text
   * or when preserving the exact binary representation is required.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "binary_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBytestringT binaryDataField;

  /**
   * The type of the item's data, in an operating specific form. For example: <code>CF_TEXT</code>
   * or <code>public.utf8-plain-text</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "clipboard_native_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT clipboardNativeTypeField;

  /**
   * The Multipurpose Internet Mail Extensions (MIME) type of the item's data. For example: <code>
   * text/plain</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mime_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT mimeTypeField;

  /** The item's data if it can be represented as a UTF-8 string. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "string_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT stringDataField;
}
