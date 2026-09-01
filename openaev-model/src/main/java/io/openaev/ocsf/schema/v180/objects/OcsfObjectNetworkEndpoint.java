package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectNetworkEndpoint extends OcsfObject {
  /** A list of <code>agent</code> objects associated with a device, endpoint, or resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent> agentListField;

  /** The Autonomous System details associated with an IP address. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "autonomous_system")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectAutonomousSystem autonomousSystemField;

  /**
   * The information describing an instance of a container. A container is a prepackaged, portable
   * system image that runs isolated on an existing system using a container runtime like
   * containerd.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer containerField;

  /** The name of the domain that the endpoint belongs to or that corresponds to the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  /**
   * Fingerprints that identify the specific application implementation on this endpoint, such as
   * Cisco NPF or HASSH.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprints")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint>
      fingerprintsField;

  /** The fully qualified name of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The endpoint hardware information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hw_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDeviceHwInfo hwInfoField;

  /** The unique identifier of a VM instance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "instance_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT instanceUidField;

  /** The name of the network interface (e.g. eth2). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT interfaceNameField;

  /** The unique identifier of the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT interfaceUidField;

  /**
   * The intermediate IP Addresses. For example, the IP addresses in the HTTP X-Forwarded-For
   * header.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "intermediate_ips")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT>
      intermediateIpsField;

  /** The IP address of the endpoint, in either IPv4 or IPv6 format. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  /** The name of the Internet Service Provider (ISP). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ispField;

  /**
   * The organization name of the Internet Service Provider (ISP). This represents the parent
   * organization or company that owns/operates the ISP. For example, Comcast Corporation would be
   * the ISP org for Xfinity internet service. This attribute helps identify the ultimate provider
   * when ISPs operate under different brand names.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp_org")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ispOrgField;

  /** The geographical location of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation locationField;

  /** The Media Access Control (MAC) address of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeMacT macField;

  /**
   * The vendor or manufacturer of the endpoint's network interface controller (NIC), as identified
   * from the MAC address.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac_vendor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT macVendorField;

  /** The short name of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * If running under a process namespace (such as in a container), the process identifier within
   * that process namespace.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT namespacePidField;

  /**
   * Indicates whether the endpoint resides inside the customer’s network, outside on the Internet,
   * or if its location relative to the customer’s network cannot be determined. The value is
   * normalized to the caption of the <code>network_scope_id</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_scope")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT networkScopeField;

  /**
   * The normalized identifier of the endpoint’s network scope. The normalized network scope
   * identifier indicates whether the endpoint resides inside the customer’s network, outside on the
   * Internet, or if its location relative to the customer’s network cannot be determined.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_scope_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT networkScopeIdField;

  /** The endpoint operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOs osField;

  /**
   * The identity of the service or user account that owns the endpoint or was last logged into it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  /** The pool of desktops or virtual machines to which the endpoint belongs. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pool")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup poolField;

  /** The port used for communication within the network connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "port")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypePortT portField;

  /**
   * The network proxy information pertaining to a specific endpoint. This can be used to describe
   * information pertaining to network address translation (NAT).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkProxy proxyEndpointField;

  /** The unique identifier of a virtual subnet. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subnetUidField;

  /**
   * The service name in service-to-service connections. For example, AWS VPC logs the
   * pkt-src-aws-service and pkt-dst-aws-service fields identify the connection is coming from or
   * going to an AWS service.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "svc_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT svcNameField;

  /**
   * The network endpoint type. For example: <code>unknown</code>, <code>server</code>, <code>
   * desktop</code>, <code>laptop</code>, <code>tablet</code>, <code>mobile</code>, <code>virtual
   * </code>, <code>browser</code>, or <code>other</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The network endpoint type ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The unique identifier of the endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The Virtual LAN identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vlan_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vlanUidField;

  /** The unique identifier of the Virtual Private Cloud (VPC). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vpc_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vpcUidField;

  /** The network zone or LAN segment. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT zoneField;
}
