package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectFunctionInvocation {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "parameters")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectParameter parametersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "return_value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT returnValueField;
}
