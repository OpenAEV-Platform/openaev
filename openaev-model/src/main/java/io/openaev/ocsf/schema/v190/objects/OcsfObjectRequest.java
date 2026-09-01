package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRequest extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "containers")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer> containersField;

  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> flagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
