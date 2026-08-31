package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEndpointConnection extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT codeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint networkEndpointField;
}
