package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDceRpc extends OcsfObject {
  /** The request command (e.g. REQUEST, BIND). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "command")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT commandField;

  /** The reply to the request command (e.g. RESPONSE, BINDACK or FAULT). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "command_response")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT commandResponseField;

  /** The list of interface flags. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> flagsField;

  /**
   * An operation number used to identify a specific remote procedure call (RPC) method or a method
   * in an interface.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "opnum")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT opnumField;

  /**
   * The RPC Interface object describes the details pertaining to the remote procedure call
   * interface.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rpc_interface")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRpcInterface rpcInterfaceField;
}
