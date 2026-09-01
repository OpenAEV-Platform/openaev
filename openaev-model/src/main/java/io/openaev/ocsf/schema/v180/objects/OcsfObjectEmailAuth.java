package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEmailAuth extends OcsfObject {
  /** The DomainKeys Identified Mail (DKIM) signing domain of the email. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim_domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dkimDomainField;

  /** The DomainKeys Identified Mail (DKIM) status of the email. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dkimField;

  /** The DomainKeys Identified Mail (DKIM) signature used by the sending/receiving system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dkim_signature")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dkimSignatureField;

  /**
   * The Domain-based Message Authentication, Reporting and Conformance (DMARC) status of the email.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dmarcField;

  /** The Domain-based Message Authentication, Reporting and Conformance (DMARC) override action. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc_override")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dmarcOverrideField;

  /** The Domain-based Message Authentication, Reporting and Conformance (DMARC) policy status. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dmarc_policy")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dmarcPolicyField;

  /** The Sender Policy Framework (SPF) status of the email. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "spf")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT spfField;
}
