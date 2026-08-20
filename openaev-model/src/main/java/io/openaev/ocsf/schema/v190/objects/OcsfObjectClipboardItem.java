package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectClipboardItem {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mime_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT mimeTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "binary_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBytestringT binaryDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "string_data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT stringDataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "clipboard_native_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT clipboardNativeTypeField;
}
