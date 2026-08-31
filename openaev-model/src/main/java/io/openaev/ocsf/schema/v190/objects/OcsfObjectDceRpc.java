package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDceRpc extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "command")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT commandField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "command_response")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT commandResponseField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "flags")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> flagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "opnum")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT opnumField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "rpc_interface")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRpcInterface rpcInterfaceField;
}
