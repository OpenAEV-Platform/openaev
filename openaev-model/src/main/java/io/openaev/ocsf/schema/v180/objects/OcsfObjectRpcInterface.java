package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRpcInterface extends OcsfObject {
  /**
   * An integer that provides a reason code or additional information about the acknowledgment
   * result.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ack_reason")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT ackReasonField;

  /** An integer that denotes the acknowledgment result of the DCE/RPC call. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ack_result")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT ackResultField;

  /** The unique identifier of the particular remote procedure or service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uuid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUuidT uuidField;

  /** The version of the DCE/RPC protocol being used in the session. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
