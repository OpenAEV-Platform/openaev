package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectKernelDriver extends OcsfObject {
  /** The driver/extension file object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFile fileField;
}
