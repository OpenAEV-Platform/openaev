package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectHttpResponse extends OcsfObject {
  /**
   * The actual length of the HTTP response body, in number of bytes, independent of a potentially
   * existing Content-Length header.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "body_length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT bodyLengthField;

  /**
   * The Hypertext Transfer Protocol (HTTP) status code returned from the web server to the client.
   * For example, 200.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT codeField;

  /**
   * The HTTP request header that identifies the original media type of the resource (prior to any
   * content encoding applied for sending).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "content_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT contentTypeField;

  /** Additional HTTP headers of an HTTP request or response. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpHeader> httpHeadersField;

  /** The HTTP response latency measured in milliseconds. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "latency")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT latencyField;

  /** The length of the entire HTTP response, in number of bytes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "length")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT lengthField;

  /** The HTTP status code and reason phrase returned from the server. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageField;

  /**
   * The response status. For example: A successful HTTP status of 'OK' which corresponds to a code
   * of 200.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;
}
