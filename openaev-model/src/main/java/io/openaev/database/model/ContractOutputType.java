package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.output_processor.*;
import io.swagger.v3.oas.annotations.Hidden;

public enum ContractOutputType {
  @JsonProperty("text")
  Text(TextOutputTypeHandler.class),
  @JsonProperty("number")
  Number(NumberOutputTypeHandler.class),
  @JsonProperty("port")
  Port(PortOutputTypeHandler.class),
  @JsonProperty("portscan")
  PortsScan(PortScanOutputTypeHandler.class),
  @JsonProperty("ipv4")
  IPv4(IPv4OutputTypeHandler.class),
  @JsonProperty("ipv6")
  IPv6(IPv6OutputTypeHandler.class),
  @JsonProperty("credentials")
  Credentials(CredentialsOutputTypeHandler.class),
  @JsonProperty("cve")
  CVE(CVEOutputTypeHandler.class),
  @Hidden
  @JsonProperty("asset")
  Asset(AssetOutputTypeHandler.class);

  public final Class<? extends ContractOutputTypeHandler> handlerClass;

  ContractOutputType(Class<? extends ContractOutputTypeHandler> handlerClass) {
    this.handlerClass = handlerClass;
  }
}
