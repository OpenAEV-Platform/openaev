package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectNetworkProxy extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "svc_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT svcNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pool")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup poolField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOs osField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAgent agentListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser ownerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation locationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vlan_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vlanUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac_vendor")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT macVendorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "autonomous_system")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAutonomousSystem autonomousSystemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vpc_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vpcUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "proxy_endpoint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkProxy proxyEndpointField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT interfaceNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT subnetUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "instance_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT instanceUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hw_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDeviceHwInfo hwInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprints")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint fingerprintsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_scope")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT networkScopeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "intermediate_ips")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT intermediateIpsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT ipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT namespacePidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "port")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypePortT portField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT zoneField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ispField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_scope_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT networkScopeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeMacT macField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT interfaceUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "isp_org")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ispOrgField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer containerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
