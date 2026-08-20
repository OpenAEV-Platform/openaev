package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDevice {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "imei_list")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT imeiListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT interfaceUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAgent agentListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vpc_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vpcUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOs osField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hw_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDeviceHwInfo hwInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "image")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectImage imageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT subnetUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer containerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeMacT macField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT riskLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "iccid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT iccidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "boot_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT bootUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pool")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup poolField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "instance_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT instanceUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hypervisor")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT hypervisorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "peripheral_devices")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPeripheralDevice peripheralDevicesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_compliant")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isCompliantField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOrganization orgField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_supervised")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSupervisedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_interfaces")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkInterface networkInterfacesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "eid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT eidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "os_machine_uuid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT osMachineUuidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT zoneField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_mobile_account_active")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isMobileAccountActiveField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "udid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT udidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "autoscale_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT autoscaleUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_trusted")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isTrustedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_backed_up")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isBackedUpField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mac_vendor")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT macVendorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vlan_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vlanUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "meid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT meidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_managed")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isManagedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT ipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT namespacePidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "imei")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT imeiField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT riskScoreField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT modelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser ownerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "boot_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT bootTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "groups")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup groupsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeSubnetT subnetField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT interfaceNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "boot_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT bootTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_personal")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isPersonalField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "location")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation locationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_shared")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSharedField;
}
