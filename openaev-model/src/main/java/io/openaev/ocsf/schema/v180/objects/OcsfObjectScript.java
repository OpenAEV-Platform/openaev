package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectScript extends OcsfObject {
  /**
   * Present if this script is associated with a file. Not present in the case of a file-less
   * script.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  /**
   * An array of the script's cryptographic hashes. Note that these hashes are calculated on the
   * script in its original encoding, and not on the normalized UTF-8 encoding found in the <code>
   * script_content</code> attribute.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hashes")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint> hashesField;

  /**
   * Unique identifier for the script or macro, independent of the containing file, used for
   * tracking, auditing, and security analysis.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * This attribute relates a sub-script to a parent script having the matching <code>uid</code>
   * attribute. In the case of PowerShell, sub-script execution can be identified by matching the
   * activity correlation ID of the raw ETW events provided by the OS.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT parentUidField;

  /**
   * The script content, normalized to UTF-8 encoding irrespective of its original encoding. When
   * emitting this attribute, it may be appropriate to truncate large scripts. When consuming this
   * attribute, large scripts should be anticipated.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "script_content")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLongString scriptContentField;

  /**
   * The script type, normalized to the caption of the <code>type_id</code> value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The normalized script type ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * Some script engines assign a unique ID to each individual execution of a given script. This
   * attribute captures that unique ID. In the case of PowerShell, the unique ID corresponds to the
   * <code>ScriptBlockId</code> in the raw ETW events provided by the OS.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
