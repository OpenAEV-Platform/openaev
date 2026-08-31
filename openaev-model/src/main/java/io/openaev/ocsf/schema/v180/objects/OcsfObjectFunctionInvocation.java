package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFunctionInvocation extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT errorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "parameters")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectParameter> parametersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "return_value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT returnValueField;
}
