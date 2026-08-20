package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDnsResourceRecord {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rdata")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT rdataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ttl")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT ttlField;
}
