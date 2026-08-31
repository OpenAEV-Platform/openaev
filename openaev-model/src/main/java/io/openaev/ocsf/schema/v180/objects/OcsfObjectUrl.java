package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectUrl extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "categories")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> categoriesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_ids")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT>
      categoryIdsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "port")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypePortT portField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT queryStringField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT resourceTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "scheme")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT schemeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subdomain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subdomainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT urlStringField;
}
