package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectDnsQuery {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "class")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT classField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "packet_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT packetUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "opcode")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT opcodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "opcode_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT opcodeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;
}
