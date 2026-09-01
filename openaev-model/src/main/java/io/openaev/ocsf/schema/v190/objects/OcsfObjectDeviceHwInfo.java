package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDeviceHwInfo extends OcsfObject {
  /** The BIOS date. For example: <code>03/31/16</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bios_date")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT biosDateField;

  /** The BIOS manufacturer. For example: <code>LENOVO</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bios_manufacturer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT biosManufacturerField;

  /** The BIOS version. For example: <code>LENOVO G5ETA2WW (2.62)</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bios_ver")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT biosVerField;

  /**
   * The chassis type describes the system enclosure or physical form factor. For example, as
   * described for a Windows chassis type.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chassis")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT chassisField;

  /**
   * The total number of processor cores across all installed CPUs on the system. For per-CPU core
   * detail, use <code>cpu_info_list</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cores")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT coresField;

  /**
   * The CPU architecture, normalized to the caption of the <code>cpu_architecture_id</code> value.
   * In the case of <code>Other</code>, it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_architecture")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpuArchitectureField;

  /** The normalized identifier of the CPU architecture. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_architecture_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuArchitectureIdField;

  /**
   * The cpu architecture, the number of bits used for addressing in memory. For example: <code>32
   * </code> or <code>64</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_bits")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuBitsField;

  /** The number of processor cores in all installed processors. For Example: <code>42</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_cores")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuCoresField;

  /**
   * The number of physical processors on a system. For per-CPU detail, use <code>cpu_info_list
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuCountField;

  /**
   * A list of <code>cpu_info</code> objects, each describing a physical CPU package installed in
   * the device. Use with <code>cpu_count</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCpuInfo> cpuInfoListField;

  /** The speed of the processor in Mhz. For Example: <code>4200</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_speed")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuSpeedField;

  /** The processor type. For example: <code>x86 Family 6 Model 37 Stepping 5</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpuTypeField;

  /** The desktop display affiliated with the event */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desktop_display")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDisplay desktopDisplayField;

  /**
   * The number of graphics processors on a system. For per-GPU detail, use <code>gpu_info_list
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "gpu_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT gpuCountField;

  /**
   * A list of GPU objects describing the hardware properties of each graphics processor installed
   * on the device. Use with <code>gpu_count</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "gpu_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectGpuInfo> gpuInfoListField;

  /** The keyboard detailed information. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyboardInfo keyboardInfoField;

  /** The total amount of installed RAM, in Megabytes. For example: <code>2048</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ram_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT ramSizeField;

  /** The device manufacturer serial number. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "serial_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serialNumberField;

  /**
   * The device manufacturer assigned universally unique hardware identifier. For SMBIOS compatible
   * devices such as those running Linux and Windows, it is the UUID member of the System
   * Information structure in the SMBIOS information. For macOS devices, it is the Hardware UUID
   * (also known as IOPlatformUUID in the I/O Registry).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT uuidField;

  /** The device manufacturer. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;
}
