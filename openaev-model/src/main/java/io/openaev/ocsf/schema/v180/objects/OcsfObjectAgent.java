package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAgent extends OcsfObject {
  /** The name of the agent or sensor. For example: <code>AWS SSM Agent</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * Describes the various policies that may be applied or enforced by an agent or sensor. E.g.,
   * Conditional Access, prevention, auto-update, tamper protection, destination configuration, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "policies")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy> policiesField;

  /**
   * The normalized caption of the type_id value for the agent or sensor. In the case of 'Other' or
   * 'Unknown', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The normalized representation of an agent or sensor. E.g., EDR, vulnerability management, APM,
   * backup & recovery, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * An alternative or contextual identifier for the agent or sensor, such as a configuration,
   * organization, or license UID.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** The UID of the agent or sensor, sometimes known as a Sensor ID or <code>aid</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The company or author who created the agent or sensor. For example: <code>Crowdstrike</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vendorNameField;

  /** The semantic version of the agent or sensor, e.g., <code>7.101.50.0</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
