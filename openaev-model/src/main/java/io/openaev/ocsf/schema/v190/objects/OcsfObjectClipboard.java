package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectClipboard extends OcsfObject {
  /** The data items written to, or read from, the clipboard. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "contents")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectClipboardItem> contentsField;

  /**
   * The name of the clipboard on systems with named clipboards. For example: <code>General</code>
   * or <code>Drag</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;
}
