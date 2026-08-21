package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectFunctionInvocation extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "return_value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT returnValueField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "parameters")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectParameter parametersField;
}
