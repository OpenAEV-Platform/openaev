package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.output_processor.*;
import io.swagger.v3.oas.annotations.Hidden;

public enum ContractOutputType {
  @JsonProperty("text")
  Text("text", TextOutputProcessorHandler.class),

  @JsonProperty("number")
  Number("number", NumberOutputProcessorHandler.class),

  @JsonProperty("port")
  Port("port", PortOutputProcessorHandler.class),

  @JsonProperty("portscan")
  PortsScan("portscan", PortScanOutputProcessorHandler.class),

  @JsonProperty("ipv4")
  IPv4("ipv4", IPv4OutputProcessorHandler.class),

  @JsonProperty("ipv6")
  IPv6("ipv6", IPv6OutputProcessorHandler.class),

  @JsonProperty("credentials")
  Credentials("credentials", CredentialsOutputProcessorHandler.class),

  @JsonProperty("cve")
  CVE("cve", CVEOutputProcessorHandler.class),

  @Hidden
  @JsonProperty("asset")
  Asset("asset", AssetOutputProcessorHandler.class);

  private final String label;
  private final Class<? extends OutputProcessorHandler> handlerClass;

  ContractOutputType(String label, Class<? extends OutputProcessorHandler> handlerClass) {
    this.label = label;
    this.handlerClass = handlerClass;
  }

  public String getLabel() {
    return label;
  }

  public Class<? extends OutputProcessorHandler> getHandlerClass() {
    return handlerClass;
  }
}
