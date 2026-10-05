package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectJobAction extends OcsfObject {
  /**
   * When <code>type_id</code> is <code>Execute (2)</code>, this describes the command line that is
   * executed.<br>
   * <br>
   * When <code>type_id</code> is <code>COM Handler (1)</code>, this <em>may</em> describe the DLL
   * path stored in the COM component's <code>InprocServer32</code> key or the command line stored
   * in the COM component's <code>LocalServer32</code> key.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cmdLineField;

  /**
   * When <code>type_id</code> is <code>COM Handler (1)</code>, this describes the COM Class
   * Identifier (CLSID).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "com_class_uuid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT comClassUuidField;

  /**
   * When known, describes the image file that is executed when <code>type_id</code> is <code>
   * COM Handler</code> or <code>Execute</code>. Note that this attribute is intended to supplement
   * the <code>com_class_uid</code> or <code>cmd_line</code> attributes, and is not an alternative
   * to them.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;

  /**
   * The list of properties associated with the performed action.<br>
   * Can be populated with additional attributes of a program execution process, COM object
   * attributes, fields of a message box or an email.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "properties")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject>
      propertiesField;

  /**
   * The job action type, normalized to the caption of the <code>type_id</code> value. In the case
   * of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The job action type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * When <code>type_id</code> is <code>Execute (2)</code>, this describes the working directory of
   * a program that is run by the job.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "working_directory")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT workingDirectoryField;
}
