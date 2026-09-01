package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEndpoint extends OcsfObject {
  /** A list of <code>agent</code> objects associated with a device, endpoint, or resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAgent> agentListField;

  /**
   * The information describing an instance of a container. A container is a prepackaged, portable
   * system image that runs isolated on an existing system using a container runtime like
   * containerd.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer containerField;

  /** The name of the domain that the endpoint belongs to or that corresponds to the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT domainField;

  /** The fully qualified name of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The endpoint hardware information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hw_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDeviceHwInfo hwInfoField;

  /** The unique identifier of a VM instance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "instance_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT instanceUidField;

  /** The name of the network interface (e.g. eth2). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT interfaceNameField;

  /** The unique identifier of the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT interfaceUidField;

  /** The IP address of the endpoint, in either IPv4 or IPv6 format. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT ipField;

  /** The geographical location of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation locationField;

  /** The Media Access Control (MAC) address of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeMacT macField;

  /**
   * The vendor or manufacturer of the endpoint's network interface controller (NIC), as identified
   * from the MAC address.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac_vendor")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT macVendorField;

  /** The short name of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * If running under a process namespace (such as in a container), the process identifier within
   * that process namespace.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT namespacePidField;

  /** The endpoint operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOs osField;

  /**
   * The identity of the service or user account that owns the endpoint or was last logged into it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser ownerField;

  /** The pool of desktops or virtual machines to which the endpoint belongs. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pool")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup poolField;

  /** The unique identifier of a virtual subnet. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT subnetUidField;

  /**
   * The endpoint type. For example: <code>unknown</code>, <code>server</code>, <code>desktop</code>
   * , <code>laptop</code>, <code>tablet</code>, <code>mobile</code>, <code>virtual</code>, <code>
   * browser</code>, or <code>other</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The endpoint type ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The unique identifier of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /** The Virtual LAN identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vlan_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vlanUidField;

  /** The unique identifier of the Virtual Private Cloud (VPC). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vpc_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vpcUidField;

  /** The network zone or LAN segment. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT zoneField;
}
