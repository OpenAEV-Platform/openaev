package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectVendorAttributes extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT severityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "severity_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT severityIdField;
}
