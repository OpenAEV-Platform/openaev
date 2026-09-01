package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCpuInfo extends OcsfObject {
  /** The number of processing cores or compute units for the component. */
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

  /** The number of bits used by the CPU for memory addressing. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_bits")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuBitsField;

  /**
   * The model name of the CPU. For example: <code>Intel Xeon Gold 6348</code>, <code>AMD EPYC 7763
   * </code>, or <code>Apple M3 Max</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT modelField;

  /** The nominal clock speed of the unit, expressed in megahertz. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "speed_mhz")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT speedMhzField;

  /**
   * The name of the vendor of the CPU. For example: <code>Intel</code>, <code>AMD</code>, or <code>
   * Apple</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;
}
