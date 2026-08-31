package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDnsAnswer extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "class")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT classField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flag_ids")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT> flagIdsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> flagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "packet_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT packetUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "rdata")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rdataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ttl")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT ttlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;
}
