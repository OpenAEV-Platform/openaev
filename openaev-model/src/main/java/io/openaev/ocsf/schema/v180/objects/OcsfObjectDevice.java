package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDevice extends OcsfObject {
  /** A list of <code>agent</code> objects associated with a device, endpoint, or resource. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent> agentListField;

  /** The unique identifier of the cloud autoscale configuration. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "autoscale_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT autoscaleUidField;

  /** The time the system was booted. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "boot_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT bootTimeDtField;

  /** The time the system was booted. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "boot_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT bootTimeField;

  /**
   * A unique identifier of the device that changes after every reboot. For example, the value of
   * <code>/proc/sys/kernel/random/boot_id</code> from Linux's procfs.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "boot_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT bootUidField;

  /**
   * The information describing an instance of a container. A container is a prepackaged, portable
   * system image that runs isolated on an existing system using a container runtime like
   * containerd.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "container")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer containerField;

  /** The time when the device was known to have been created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the device was known to have been created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The description of the device, ordinarily as reported by the operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /** The network domain where the device resides. For example: <code>work.example.com</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  /**
   * An Embedded Identity Document, is a unique serial number that identifies an eSIM-enabled
   * device.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "eid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT eidField;

  /** The initial discovery time of the device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  /** The initial discovery time of the device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  /**
   * The group names to which the device belongs. For example: <code>
   * ["Windows Laptops", "Engineering"]</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "groups")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup> groupsField;

  /** The device hostname. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  /** The endpoint hardware information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hw_info")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDeviceHwInfo hwInfoField;

  /**
   * The name of the hypervisor running on the device. For example, <code>Xen</code>, <code>VMware
   * </code>, <code>Hyper-V</code>, <code>VirtualBox</code>, etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hypervisor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT hypervisorField;

  /**
   * The Integrated Circuit Card Identification of a mobile device. Typically it is a unique 18 to
   * 22 digit number that identifies a SIM card.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "iccid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT iccidField;

  /** The image used as a template to run the virtual machine. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "image")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectImage imageField;

  /** The International Mobile Equipment Identity that is associated with the device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "imei")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT imeiField;

  /** The International Mobile Equipment Identity values that are associated with the device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "imei_list")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> imeiListField;

  /** The unique identifier of a VM instance. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "instance_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT instanceUidField;

  /** The name of the network interface (e.g. eth2). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT interfaceNameField;

  /** The unique identifier of the network interface. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "interface_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT interfaceUidField;

  /** The device IP address, in either IPv4 or IPv6 format. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  /**
   * Indicates whether the device or resource has a backup enabled, such as an automated snapshot or
   * a cloud backup. For example, this is indicated by the <code>cloudBackupEnabled</code> value
   * within JAMF Pro mobile devices or the registration of an AWS ARN with the AWS Backup service.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_backed_up")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isBackedUpField;

  /** The event occurred on a compliant device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_compliant")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isCompliantField;

  /** The event occurred on a managed device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_managed")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isManagedField;

  /**
   * Indicates whether the device has an active mobile account. For example, this is indicated by
   * the <code>itunesStoreAccountActive</code> value within JAMF Pro mobile devices.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_mobile_account_active")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isMobileAccountActiveField;

  /** The event occurred on a personal device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_personal")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isPersonalField;

  /** The event occurred on a shared device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_shared")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSharedField;

  /**
   * The event occurred on a supervised device. Devices that are supervised are typically mobile
   * devices managed by a Mobile Device Management solution and are restricted from specific
   * behaviors such as Apple AirDrop.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_supervised")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSupervisedField;

  /** The event occurred on a trusted device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_trusted")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isTrustedField;

  /** The most recent discovery time of the device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  /** The most recent discovery time of the device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  /** The geographical location of the device. */
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

  /**
   * The Mobile Equipment Identifier. It's a unique number that identifies a Code Division Multiple
   * Access (CDMA) mobile device.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "meid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT meidField;

  /** The model of the device. For example <code>ThinkPad X1 Carbon</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT modelField;

  /** The time when the device was last known to have been modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the device was last known to have been modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /**
   * The alternate device name, ordinarily as assigned by an administrator.
   *
   * <p><b>Note:</b> The <b>Name</b> could be any other string that helps to identify the device,
   * such as a phone number; for example <code>310-555-1234</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * If running under a process namespace (such as in a container), the process identifier within
   * that process namespace.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace_pid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT namespacePidField;

  /**
   * The physical or virtual network interfaces that are associated with the device, one for each
   * unique MAC address/IP address/hostname/name combination.
   *
   * <p><b>Note:</b> The first element of the array is the network information that pertains to the
   * event.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_interfaces")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkInterface>
      networkInterfacesField;

  /** Organization and org unit related to the device. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOrganization orgField;

  /** The endpoint operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "os")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectOs osField;

  /**
   * The operating system assigned Machine ID. In Windows, this is the value stored at the registry
   * path: <code>HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Cryptography\MachineGuid</code>. In Linux,
   * this is stored in the file: <code>/etc/machine-id</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "os_machine_uuid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUuidT osMachineUuidField;

  /**
   * The identity of the service or user account that owns the endpoint or was last logged into it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  /** The pool of desktops or virtual machines to which the endpoint belongs. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pool")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup poolField;

  /** The region where the virtual machine is located. For example, an AWS Region. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT regionField;

  /** The risk level, normalized to the caption of the risk_level_id value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT riskLevelField;

  /** The normalized risk level id. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_level_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskLevelIdField;

  /** The risk score as reported by the event source. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "risk_score")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT riskScoreField;

  /** The subnet mask. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeSubnetT subnetField;

  /** The unique identifier of a virtual subnet. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subnet_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subnetUidField;

  /**
   * The device type. For example: <code>unknown</code>, <code>server</code>, <code>desktop</code>,
   * <code>laptop</code>, <code>tablet</code>, <code>mobile</code>, <code>virtual</code>, <code>
   * browser</code>, or <code>other</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  /** The device type ID. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The Apple assigned Unique Device Identifier (UDID). For iOS, iPadOS, tvOS, watchOS and visionOS
   * devices, this is the UDID. For macOS devices, it is the Provisioning UDID. For example: <code>
   * 00008020-008D4548007B4F26</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "udid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT udidField;

  /** An alternate unique identifier of the device if any. For example the ActiveDirectory DN. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  /** The unique identifier of the device. For example the Windows TargetSID or AWS EC2 ARN. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The vendor for the device. For example <code>Dell</code> or <code>Lenovo</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vendorNameField;

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
