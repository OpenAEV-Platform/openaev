package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectParameter extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "post_value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT postValueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pre_value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT preValueField;
}
