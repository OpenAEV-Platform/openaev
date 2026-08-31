package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEndpoint extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent> agentListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer containerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hw_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDeviceHwInfo hwInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "instance_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT instanceUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT interfaceNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT interfaceUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation locationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeMacT macField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac_vendor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT macVendorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT namespacePidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOs osField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pool")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup poolField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subnetUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vlan_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vlanUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vpc_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vpcUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT zoneField;
}
