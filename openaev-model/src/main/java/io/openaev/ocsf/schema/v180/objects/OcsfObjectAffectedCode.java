package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAffectedCode extends OcsfObject {
  /** The column number of the last part of the assessed code identified as vulnerable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_column")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT endColumnField;

  /** The line number of the last line of code block identified as vulnerable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "end_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT endLineField;

  /** Details about the file that contains the affected code block. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  /** Details about the user that owns the affected file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  /** Describes the recommended remediation steps to address identified issue(s). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "remediation")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRemediation remediationField;

  /**
   * Details about the specific rule, e.g., those defined as part of a larger <code>policy</code>,
   * that triggered the finding.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rule")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectRule ruleField;

  /** The column number of the first part of the assessed code identified as vulnerable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_column")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT startColumnField;

  /** The line number of the first line of code block identified as vulnerable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_line")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT startLineField;
}
