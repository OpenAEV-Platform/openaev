package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectNetworkInterface extends OcsfObject {
  /** The hostname associated with the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The IP address associated with the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  /** The MAC address of the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeMacT macField;

  /** The name of the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The namespace is useful in merger or acquisition situations. For example, when similar entities
   * exist that you need to keep separate.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT namespaceField;

  /**
   * The list of open ports on a network interface, including port numbers and associated protocol
   * information.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "open_ports")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectPortInfo> openPortsField;

  /**
   * The subnet prefix length determines the number of bits used to represent the network part of
   * the IP address. The remaining bits are reserved for identifying individual hosts within that
   * subnet.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_prefix")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT subnetPrefixField;

  /** The type of network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The network interface type identifier. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /** The unique identifier for the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
