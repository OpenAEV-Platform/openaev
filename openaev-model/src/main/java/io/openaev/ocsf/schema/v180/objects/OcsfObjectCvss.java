package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCvss extends OcsfObject {
  /** The CVSS base score. For example: <code>9.1</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "base_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeFloatT baseScoreField;

  /** The CVSS depth represents a depth of the equation used to calculate CVSS score. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "depth")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT depthField;

  /**
   * The Common Vulnerability Scoring System metrics. This attribute contains information on the
   * CVE's impact. If the CVE has been analyzed, this attribute will contain any CVSSv2 or CVSSv3
   * information associated with the vulnerability. For example: <code>
   * { {"Access Vector", "Network"}, {"Access Complexity", "Low"}, ...}</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "metrics")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectMetric> metricsField;

  /**
   * The CVSS overall score, impacted by base, temporal, and environmental metrics. For example:
   * <code>9.1</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "overall_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeFloatT overallScoreField;

  /**
   * The Common Vulnerability Scoring System (CVSS) Qualitative Severity Rating. A textual
   * representation of the numeric score.<strong>CVSS v2.0</strong>
   *
   * <ul>
   *   <li>Low (0.0 – 3.9)
   *   <li>Medium (4.0 – 6.9)
   *   <li>High (7.0 – 10.0)
   * </ul>
   *
   * <strong>CVSS v3.0</strong>
   *
   * <ul>
   *   <li>None (0.0)
   *   <li>Low (0.1 - 3.9)
   *   <li>Medium (4.0 - 6.9)
   *   <li>High (7.0 - 8.9)
   *   <li>Critical (9.0 - 10.0)
   * </ul>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  /**
   * The source URL for the CVSS score. For example: <code>
   * https://nvd.nist.gov/vuln/detail/CVE-2021-44228</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * The CVSS vector string is a text representation of a set of CVSS metrics. It is commonly used
   * to record or transfer CVSS metric information in a concise form. For example: <code>
   * 3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:H</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vector_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vectorStringField;

  /** The vendor that provided the CVSS score. For example: <code>NVD, REDHAT</code> etc. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vendorNameField;

  /** The CVSS version. For example: <code>3.1</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
