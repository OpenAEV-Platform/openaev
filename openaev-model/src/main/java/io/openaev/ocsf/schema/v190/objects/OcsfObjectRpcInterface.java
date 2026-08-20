package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectRpcInterface {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ack_result")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT ackResultField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUuidT uuidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ack_reason")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT ackReasonField;
}
