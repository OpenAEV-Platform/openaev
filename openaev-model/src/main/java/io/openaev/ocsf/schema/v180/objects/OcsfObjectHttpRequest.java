package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectHttpRequest extends OcsfObject {
  /** The arguments sent along with the HTTP request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "args")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT argsField;

  /**
   * The actual length of the HTTP request body, in number of bytes, independent of a potentially
   * existing Content-Length header.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "body_length")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT bodyLengthField;

  /** Additional HTTP headers of an HTTP request or response. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpHeader> httpHeadersField;

  /**
   * The <a target='_blank' href='https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods'>HTTP
   * request method</a> indicates the desired action to be performed for a given resource.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_method")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT httpMethodField;

  /** The length of the entire HTTP request, in number of bytes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "length")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT lengthField;

  /**
   * The request header that identifies the address of the previous web page, which is linked to the
   * current web page or resource being requested.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "referrer")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT referrerField;

  /** The unique identifier of the http request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The URL object that pertains to the request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl urlField;

  /** The request header that identifies the operating system and web browser. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "user_agent")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT userAgentField;

  /** The Hypertext Transfer Protocol (HTTP) version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;

  /**
   * The X-Forwarded-For header identifying the originating IP address(es) of a client connecting to
   * a web server through an HTTP proxy or a load balancer.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "x_forwarded_for")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT> xForwardedForField;
}
