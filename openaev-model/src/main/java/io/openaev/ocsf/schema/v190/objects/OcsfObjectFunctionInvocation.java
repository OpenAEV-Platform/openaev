package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFunctionInvocation extends OcsfObject {
  /**
   * The error indication returned from the function. This may differ from the return value (e.g.
   * when <code>errno</code> is used).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "error")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT errorField;

  /** The parameters passed into a function invocation. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "parameters")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectParameter> parametersField;

  /** The value returned from a function. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "return_value")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT returnValueField;
}
