package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEndpointConnection extends OcsfObject {
  /** A numerical response status code providing details about the connection. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT codeField;

  /** Provides characteristics of the network endpoint. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_endpoint")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint networkEndpointField;
}
