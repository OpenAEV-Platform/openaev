package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDisplay extends OcsfObject {
  /** The numeric color depth. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "color_depth")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT colorDepthField;

  /** The numeric physical height of display. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "physical_height")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT physicalHeightField;

  /** The numeric physical orientation of display. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "physical_orientation")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT physicalOrientationField;

  /** The numeric physical width of display. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "physical_width")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT physicalWidthField;

  /** The numeric scale factor of display. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "scale_factor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT scaleFactorField;
}
