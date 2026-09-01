package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCompliance extends OcsfObject {
  /** A list of assessments associated with the compliance requirements evaluation. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "assessments")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAssessment> assessmentsField;

  /**
   * The category a control framework pertains to, as reported by the source tool, such as <code>
   * Asset Management</code> or <code>Risk Assessment</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT categoryField;

  /**
   * A list of compliance checks associated with specific industry standards or frameworks. Each
   * check represents an individual rule or requirement that has been evaluated against a target
   * device. Checks typically include details such as the check name (e.g., CIS: 'Ensure mounting of
   * cramfs filesystems is disabled' or DISA STIG descriptive titles), unique identifiers (such as
   * CIS identifier '1.1.1.1' or DISA STIG identifier 'V-230234'), descriptions (detailed
   * explanations of security requirements or vulnerability discussions), and version information.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "checks")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectCheck> checksField;

  /**
   * A list of reference KB articles that provide information to help organizations understand,
   * interpret, and implement compliance standards. They provide guidance, best practices, and
   * examples.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "compliance_references")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKbArticle>
      complianceReferencesField;

  /**
   * A list of established guidelines or criteria that define specific requirements an organization
   * must follow.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "compliance_standards")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKbArticle>
      complianceStandardsField;

  /**
   * A Control is a prescriptive, actionable set of specifications that strengthens device posture.
   * The control specifies required security measures, while the specific implementation values are
   * defined in control_parameters. E.g., CIS AWS Foundations Benchmark 1.2.0 - Control 2.1 - Ensure
   * CloudTrail is enabled in all regions
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "control")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT controlField;

  /**
   * The list of control parameters evaluated in a Compliance check. E.g., parameters for CloudTrail
   * configuration might include <code>multiRegionTrailEnabled: true</code>, <code>
   * logFileValidationEnabled: true</code>, and <code>requiredRegions: [us-east-1, us-west-2]</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "control_parameters")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject>
      controlParametersField;

  /** The description or criteria of a control. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * The specific compliance requirements being evaluated. E.g., <code>
   * PCI DSS Requirement 8.2.3 - Passwords must meet minimum complexity requirements</code> or
   * <code>HIPAA Security Rule 164.312(a)(2)(iv) - Implement encryption and decryption mechanisms
   * </code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "requirements")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      requirementsField;

  /** The regulatory or industry standards being evaluated for compliance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "standards")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> standardsField;

  /** The resultant status code of the compliance check. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusCodeField;

  /** The contextual description of the <code>status, status_code</code> values. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_detail")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusDetailField;

  /** A list of contextual descriptions of the <code>status, status_code</code> values. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_details")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      statusDetailsField;

  /**
   * The resultant status of the compliance check normalized to the caption of the <code>status_id
   * </code> value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusField;

  /** The normalized status identifier of the compliance check. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT statusIdField;
}
