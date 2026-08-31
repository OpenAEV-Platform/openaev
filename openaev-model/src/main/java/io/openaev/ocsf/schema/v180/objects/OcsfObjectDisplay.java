package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDisplay extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "color_depth")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT colorDepthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "physical_height")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT physicalHeightField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "physical_orientation")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT physicalOrientationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "physical_width")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT physicalWidthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "scale_factor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT scaleFactorField;
}
