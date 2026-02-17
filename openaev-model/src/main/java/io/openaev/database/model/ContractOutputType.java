package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.output_processor.*;
import io.swagger.v3.oas.annotations.Hidden;

public enum ContractOutputType {
  @JsonProperty("text")
  Text(TextOutputProcessorHandler.class),
  @JsonProperty("number")
  Number(NumberOutputProcessorHandler.class),
  @JsonProperty("port")
  Port(PortOutputProcessorHandler.class),
  @JsonProperty("portscan")
  PortsScan(PortScanOutputProcessorHandler.class),
  @JsonProperty("ipv4")
  IPv4(IPv4OutputProcessorHandler.class),
  @JsonProperty("ipv6")
  IPv6(IPv6OutputProcessorHandler.class),
  @JsonProperty("credentials")
  Credentials(CredentialsOutputProcessorHandler.class),
  @JsonProperty("cve")
  CVE(CVEOutputProcessorHandler.class),
  @Hidden
  @JsonProperty("asset")
  Asset(AssetOutputProcessorHandler.class);

  public final Class<? extends StructuredOutputProcessorHandler> handlerClass;

  ContractOutputType(Class<? extends StructuredOutputProcessorHandler> handlerClass) {
    this.handlerClass = handlerClass;
  }
}
