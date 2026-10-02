package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDnsResourceRecord extends OcsfObject {
  /**
   * The class of DNS data contained in this resource record. See RFC 1035. For example: <code>IN
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classField;

  /** The owner name of the resource record. For example: <code>www.example.com</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  /**
   * The data describing the DNS resource. The meaning of this data depends on the type and class of
   * the resource record.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rdata")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT rdataField;

  /**
   * The time interval that the resource record may be cached. Zero value means that the resource
   * record can only be used for the transaction in progress, and should not be cached.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ttl")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT ttlField;

  /**
   * The type of data contained in this resource record. See RFC 1035. For example: <code>CNAME
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;
}
