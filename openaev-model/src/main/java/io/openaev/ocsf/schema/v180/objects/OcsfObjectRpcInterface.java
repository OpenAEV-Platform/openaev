package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRpcInterface extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ack_reason")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT ackReasonField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ack_result")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT ackResultField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUuidT uuidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
