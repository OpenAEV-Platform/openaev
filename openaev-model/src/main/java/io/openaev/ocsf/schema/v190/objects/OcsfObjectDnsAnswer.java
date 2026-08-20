package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDnsAnswer {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flag_ids")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT flagIdsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT flagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ttl")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT ttlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "packet_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT packetUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "rdata")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT rdataField;
}
