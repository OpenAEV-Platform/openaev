package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCisCsc extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "control")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT controlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
