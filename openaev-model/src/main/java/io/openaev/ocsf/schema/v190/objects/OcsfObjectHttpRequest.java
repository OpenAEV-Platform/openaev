package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectHttpRequest {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user_agent")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT userAgentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpHeader httpHeadersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "args")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT argsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUrl urlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "x_forwarded_for")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT xForwardedForField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_method")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT httpMethodField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "body_length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT bodyLengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT lengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "referrer")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT referrerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
