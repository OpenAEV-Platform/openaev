package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDeviceHwInfo extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bios_date")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT biosDateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bios_manufacturer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT biosManufacturerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bios_ver")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT biosVerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "chassis")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT chassisField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cores")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT coresField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_architecture")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpuArchitectureField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_architecture_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuArchitectureIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_bits")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuBitsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_cores")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuCoresField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectCpuInfo> cpuInfoListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_speed")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuSpeedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpuTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desktop_display")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDisplay desktopDisplayField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "gpu_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT gpuCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "gpu_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectGpuInfo> gpuInfoListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "keyboard_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyboardInfo keyboardInfoField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ram_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT ramSizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "serial_number")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serialNumberField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT uuidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;
}
