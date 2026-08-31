package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectHttpRequest extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "args")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT argsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "body_length")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT bodyLengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpHeader> httpHeadersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_method")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT httpMethodField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "length")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT lengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "referrer")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT referrerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl urlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "user_agent")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT userAgentField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "x_forwarded_for")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT> xForwardedForField;
}
