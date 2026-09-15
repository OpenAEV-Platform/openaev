package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectWhois extends OcsfObject {
  /** The autonomous system information associated with a domain. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "autonomous_system")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAutonomousSystem autonomousSystemField;

  /** When the domain was registered or WHOIS entry was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** When the domain was registered or WHOIS entry was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The normalized value of dnssec_status_id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dnssec_status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dnssecStatusField;

  /** Describes the normalized status of DNS Security Extensions (DNSSEC) for a domain. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "dnssec_status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT dnssecStatusIdField;

  /** An array of <code>Domain Contact</code> objects. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain_contacts")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDomainContact>
      domainContactsField;

  /** The domain name corresponding to the WHOIS record. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT domainField;

  /** The email address for the registrar's abuse contact */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT emailAddrField;

  /** The name of the Internet Service Provider (ISP). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ispField;

  /**
   * The organization name of the Internet Service Provider (ISP). This represents the parent
   * organization or company that owns/operates the ISP. For example, Comcast Corporation would be
   * the ISP org for Xfinity internet service. This attribute helps identify the ultimate provider
   * when ISPs operate under different brand names.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp_org")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ispOrgField;

  /** When the WHOIS record was last updated or seen at. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  /** When the WHOIS record was last updated or seen at. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  /** A collection of name servers related to a domain registration or other record. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name_servers")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      nameServersField;

  /** The phone number for the registrar's abuse contact */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT phoneNumberField;

  /** The domain registrar. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "registrar")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT registrarField;

  /**
   * The status of a domain and its ability to be transferred, e.g., <code>clientTransferProhibited
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  /**
   * An array of subdomain strings. Can be used to collect several subdomains such as those from
   * Domain Generation Algorithms (DGAs).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subdomains")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> subdomainsField;

  /** The IP address block (CIDR) associated with a domain. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeSubnetT subnetField;
}
