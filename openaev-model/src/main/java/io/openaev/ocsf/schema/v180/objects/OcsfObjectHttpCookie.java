package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectHttpCookie extends OcsfObject {
  /** The domain name for the server from which the http_cookie is served. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  /** The expiration time of the HTTP cookie. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  /** The expiration time of the HTTP cookie. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  /** A cookie attribute to make it inaccessible via JavaScript */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_only")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT httpOnlyField;

  /** This attribute prevents the cookie from being accessed via JavaScript. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_http_only")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isHttpOnlyField;

  /**
   * The cookie attribute indicates that cookies are sent to the server only when the request is
   * encrypted using the HTTPS protocol.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_secure")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSecureField;

  /** The HTTP cookie name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The path of the HTTP cookie. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  /**
   * The cookie attribute that lets servers specify whether/when cookies are sent with cross-site
   * requests. Values are: Strict, Lax or None
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "samesite")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT samesiteField;

  /**
   * The cookie attribute to only send cookies to the server with an encrypted request over the
   * HTTPS protocol.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "secure")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT secureField;

  /** The HTTP cookie value. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;
}
