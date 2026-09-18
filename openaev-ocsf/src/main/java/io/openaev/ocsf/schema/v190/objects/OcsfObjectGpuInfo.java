package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectGpuInfo extends OcsfObject {
  /**
   * The attachment bus or interface standard, normalized to the caption of the bus_type_id value.
   * In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bus_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT busTypeField;

  /** The normalized identifier of the attachment bus or interface standard. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bus_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT busTypeIdField;

  /** The number of processing cores or compute units for the component. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cores")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT coresField;

  /**
   * The model name of the GPU. For example: <code>GeForce RTX A6000</code>, <code>Radeon PRO W7900
   * </code>, or <code>Intel Data Center GPU Max 1550</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT modelField;

  /**
   * The name of the vendor of the GPU. For example: <code>NVIDIA</code>, <code>AMD</code>, or
   * <code>Intel</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  /**
   * The video memory attachment mode, indicating how the VRAM hardware is integrated with the
   * system (e.g., shared or dedicated), normalized to the caption of the vram_mode_id value. For
   * 'Other', the exact attachment mode is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vram_mode")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vramModeField;

  /** The normalized identifier of the video memory attachment mode. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vram_mode_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT vramModeIdField;

  /** The total amount of installed video RAM. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vram_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT vramSizeField;
}
