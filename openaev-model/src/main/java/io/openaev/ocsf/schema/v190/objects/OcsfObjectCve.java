package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCve extends OcsfObject {
  /**
   * The Record Creation Date identifies when the CVE ID was issued to a CVE Numbering Authority
   * (CNA) or the CVE Record was published on the CVE List. Note that the Record Creation Date does
   * not necessarily indicate when this vulnerability was discovered, shared with the affected
   * vendor, publicly disclosed, or updated in CVE.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /**
   * The Record Creation Date identifies when the CVE ID was issued to a CVE Numbering Authority
   * (CNA) or the CVE Record was published on the CVE List. Note that the Record Creation Date does
   * not necessarily indicate when this vulnerability was discovered, shared with the affected
   * vendor, publicly disclosed, or updated in CVE.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /**
   * The CVSS object details Common Vulnerability Scoring System scores from the advisory that are
   * related to the vulnerability.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cvss")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCvss> cvssField;

  /**
   * The CWE object represents a weakness in a software system that can be exploited by a threat
   * actor to perform an attack. The CWE object is based on the Common Weakness Enumeration (CWE)
   * catalog.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cwe")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCwe cweField;

  /** The Common Weakness Enumeration (CWE) unique identifier. For example: <code>CWE-787</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cwe_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cweUidField;

  /**
   * Common Weakness Enumeration (CWE) definition URL. For example: <code>
   * https://cwe.mitre.org/data/definitions/787.html</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cwe_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT cweUrlField;

  /** A brief description of the CVE Record. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /**
   * The Exploit Prediction Scoring System (EPSS) object describes the estimated probability a
   * vulnerability will be exploited. EPSS is a community-driven effort to combine descriptive
   * information about vulnerabilities (CVEs) with evidence of actual exploitation in-the-wild.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "epss")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEpss epssField;

  /** The Record Modified Date identifies when the CVE record was last updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The Record Modified Date identifies when the CVE record was last updated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The product where the vulnerability was discovered. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  /** A list of reference URLs with additional information about the CVE Record. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> referencesField;

  /** Describes the Common Weakness Enumeration details related to the CVE Record. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_cwes")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCwe> relatedCwesField;

  /** A title or a brief phrase summarizing the CVE record. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT titleField;

  /**
   * The vulnerability type as classified in the CVE catalog.Most frequently used vulnerability
   * types are: <code>DoS</code>, <code>Code Execution</code>, <code>Overflow</code>, <code>
   * Memory Corruption</code>, <code>Sql Injection</code>, <code>XSS</code>, <code>
   * Directory Traversal</code>, <code>Http Response Splitting</code>, <code>Bypass something</code>
   * , <code>Gain Information</code>, <code>Gain Privileges</code>, <code>CSRF</code>, <code>
   * File Inclusion</code>. For more information see Vulnerabilities By Type distributions.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The Common Vulnerabilities and Exposures unique number assigned to a specific computer
   * vulnerability. A CVE Identifier begins with 4 digits representing the year followed by a
   * sequence of digits that acts as a unique identifier. For example: <code>CVE-2021-12345</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
