package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectWhois extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "autonomous_system")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectAutonomousSystem autonomousSystemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dnssec_status")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT dnssecStatusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "dnssec_status_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT dnssecStatusIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain_contacts")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDomainContact>
      domainContactsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "email_addr")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT emailAddrField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ispField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp_org")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ispOrgField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name_servers")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      nameServersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "phone_number")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT phoneNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "registrar")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT registrarField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT statusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subdomains")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> subdomainsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeSubnetT subnetField;
}
