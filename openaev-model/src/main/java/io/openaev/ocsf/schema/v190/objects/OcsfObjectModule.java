package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectModule {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "function_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT functionNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT loadTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_address")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT startAddressField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT loadTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "base_address")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT baseAddressField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "function_invocation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFunctionInvocation functionInvocationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile fileField;
}
