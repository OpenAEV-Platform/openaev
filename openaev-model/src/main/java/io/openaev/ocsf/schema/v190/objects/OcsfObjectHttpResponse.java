package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectHttpResponse extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "body_length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT bodyLengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT codeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "content_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT contentTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpHeader> httpHeadersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "latency")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT latencyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT lengthField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;
}
