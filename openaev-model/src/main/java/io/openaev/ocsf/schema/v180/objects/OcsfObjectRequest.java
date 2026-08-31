package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRequest extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "containers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer> containersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> flagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
