package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectClipboard extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "contents")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectClipboardItem> contentsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;
}
