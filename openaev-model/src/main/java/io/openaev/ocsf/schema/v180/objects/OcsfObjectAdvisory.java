package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAdvisory extends OcsfObject {
  /** The average time to patch. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "avg_timespan")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectTimespan avgTimespanField;

  /** The Advisory bulletin identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bulletin")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT bulletinField;

  /** The vendors classification of the Advisory. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "classification")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classificationField;

  /** The time when the Advisory record was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the Advisory record was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** A brief description of the Advisory Record. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /** The install state of the Advisory. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT installStateField;

  /** The normalized install state ID of the Advisory. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "install_state_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT installStateIdField;

  /** The Advisory has been replaced by another. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_superseded")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSupersededField;

  /** The time when the Advisory record was last updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the Advisory record was last updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The operating system the Advisory applies to. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOs osField;

  /** The product where the vulnerability was discovered. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct productField;

  /**
   * A list of reference URLs with additional information about the vulnerabilities disclosed in the
   * Advisory.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> referencesField;

  /**
   * A list of Common Vulnerabilities and Exposures <a target='_blank'
   * href='https://cve.mitre.org/'>(CVE)</a> identifiers related to the vulnerabilities disclosed in
   * the Advisory.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_cves")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectCve> relatedCvesField;

  /**
   * A list of Common Weakness Enumeration <a target='_blank'
   * href='https://cwe.mitre.org/'>(CWE)</a> identifiers related to the vulnerabilities disclosed in
   * the Advisory.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_cwes")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectCwe> relatedCwesField;

  /** The size in bytes for the Advisory. Usually populated for a KB Article patch. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  /** The Advisory link from the source vendor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /** A title or a brief phrase summarizing the Advisory. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT titleField;

  /**
   * The unique identifier assigned to the advisory or disclosed vulnerability, e.g, <code>
   * GHSA-5mrr-rgp6-x4gr</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
