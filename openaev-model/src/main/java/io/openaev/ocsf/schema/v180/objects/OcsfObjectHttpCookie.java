package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectHttpCookie extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT expirationTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "expiration_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT expirationTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_only")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT httpOnlyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_http_only")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isHttpOnlyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_secure")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSecureField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "samesite")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT samesiteField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "secure")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT secureField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "value")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT valueField;
}
