package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectCpuInfo {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_architecture_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuArchitectureIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_bits")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuBitsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "speed_mhz")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT speedMhzField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT modelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cores")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT coresField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_architecture")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpuArchitectureField;
}
