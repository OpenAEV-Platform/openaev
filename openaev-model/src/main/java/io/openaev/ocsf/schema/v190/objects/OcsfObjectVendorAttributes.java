package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectVendorAttributes extends OcsfObject {
  /**
   * The finding severity, as reported by the Vendor (Finding Provider). The value should be
   * normalized to the caption of the <code>severity_id</code> value. In the case of 'Other', it is
   * defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT severityField;

  /** The finding severity ID, as reported by the Vendor (Finding Provider). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT severityIdField;
}
