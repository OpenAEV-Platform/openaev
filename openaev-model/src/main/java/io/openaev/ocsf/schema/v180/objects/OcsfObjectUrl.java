package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectUrl extends OcsfObject {
  /** The Website categorization names, as defined by <code>category_ids</code> enum values. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "categories")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> categoriesField;

  /** The Website categorization identifiers. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_ids")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT>
      categoryIdsField;

  /**
   * The domain portion of the URL. For example: <code>example.com</code> in <code>
   * https://sub.example.com</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "domain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT domainField;

  /**
   * The URL host as extracted from the URL. For example: <code>www.example.com</code> from <code>
   * www.example.com/download/trouble</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  /**
   * The URL path as extracted from the URL. For example: <code>/download/trouble</code> from <code>
   * www.example.com/download/trouble</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  /** The URL port. For example: <code>80</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "port")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypePortT portField;

  /**
   * The query portion of the URL. For example: the query portion of the URL <code>
   * http://www.example.com/search?q=bad&sort=date</code> is <code>q=bad&sort=date</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "query_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT queryStringField;

  /** The context in which a resource was retrieved in a web request. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT resourceTypeField;

  /**
   * The scheme portion of the URL. For example: <code>http</code>, <code>https</code>, <code>ftp
   * </code>, or <code>sftp</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "scheme")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT schemeField;

  /**
   * The subdomain portion of the URL. For example: <code>sub</code> in <code>
   * https://sub.example.com</code> or <code>sub2.sub1</code> in <code>https://sub2.sub1.example.com
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subdomain")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subdomainField;

  /**
   * The URL string. See RFC 1738. For example: <code>http://www.example.com/download/trouble.exe
   * </code>. Note: The URL path should not populate the URL string.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url_string")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT urlStringField;
}
