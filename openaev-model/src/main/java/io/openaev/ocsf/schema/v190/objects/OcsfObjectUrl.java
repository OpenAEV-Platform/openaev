package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectUrl {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "scheme")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT schemeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "port")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypePortT portField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "categories")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoriesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subdomain")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT subdomainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT urlStringField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_ids")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT categoryIdsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT resourceTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_string")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT queryStringField;
}
