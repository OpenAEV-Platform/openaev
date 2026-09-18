package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCheck extends OcsfObject {
  /**
   * The detailed description of the compliance check, explaining the security requirement,
   * vulnerability, or configuration being assessed. For example, CIS: <code>
   * The cramfs filesystem type is a compressed read-only Linux filesystem. Removing support for unneeded filesystem types reduces the local attack surface.
   * </code> or DISA STIG: <code>
   * Unauthorized access to the information system by foreign entities may result in loss or compromise of data.
   * </code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /**
   * The name or title of the compliance check. For example, CIS: <code>
   * Ensure mounting of cramfs filesystems is disabled</code> or DISA STIG: <code>
   * The Ubuntu operating system must implement DoD-approved encryption to protect the confidentiality of remote access sessions
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** Describes details about the resource that this check evaluated. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectResourceDetails resourceField;

  /**
   * The severity level as defined in the source document. For example CIS Benchmarks, valid values
   * are: <code>Level 1</code> (security-forward, essential settings), <code>Level 2</code>
   * (security-focused environment, more restrictive), or <code>Scored/Not Scored</code> (whether
   * compliance can be automatically checked). For DISA STIG, valid values are: <code>CAT I</code>
   * (maps to severity_id 5/Critical), <code>CAT II</code> (maps to severity_id 4/High), or <code>
   * CAT III</code> (maps to severity_id 3/Medium).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  /**
   * The normalized severity identifier that maps severity levels to standard severity levels. For
   * example CIS Benchmark: <code>Level 2</code> maps to <code>4</code> (High), <code>Level 1</code>
   * maps to <code>3</code> (Medium). For DISA STIG: <code>CAT I</code> maps to <code>5</code>
   * (Critical), <code>CAT II</code> maps to <code>4</code> (High), and <code>CAT III</code> maps to
   * <code>3</code> (Medium).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT severityIdField;

  /**
   * The regulatory or industry standard this check is associated with. E.g., <code>PCI DSS 3.2.1
   * </code>, <code>HIPAA Security Rule</code>, <code>NIST SP 800-53 Rev. 5</code>, or <code>
   * ISO/IEC 27001:2013</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "standards")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> standardsField;

  /**
   * The resultant status of the compliance check normalized to the caption of the <code>status_id
   * </code> value. For example, CIS Benchmark: <code>Pass</code> when all requirements are met,
   * <code>Fail</code> when requirements are not met, or DISA STIG: <code>NotAFinding</code> (maps
   * to status_id 1/Pass), <code>Open</code> (maps to status_id 3/Fail).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  /** The normalized status identifier of the compliance check. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  /**
   * The unique identifier of the compliance check within its standard or framework. For example,
   * CIS Benchmark identifier <code>1.1.1.1</code>, DISA STIG identifier <code>V-230234</code>, or
   * NIST control identifier <code>AC-17(2)</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The check version. For example, CIS Benchmark: <code>1.1.0</code> for Amazon Linux 2 or DISA
   * STIG: <code>V2R1</code> for Windows 10.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
