package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectGpuInfo extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bus_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT busTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bus_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT busTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cores")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT coresField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "model")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT modelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vendorNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vram_mode")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT vramModeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vram_mode_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT vramModeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "vram_size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT vramSizeField;
}
