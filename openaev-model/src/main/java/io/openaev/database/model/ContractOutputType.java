package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Hidden;

public enum ContractOutputType {
  @JsonProperty("text")
  Text("text"),

  @JsonProperty("number")
  Number("number"),

  @JsonProperty("port")
  Port("port"),

  @JsonProperty("portscan")
  PortsScan("portscan"),

  @JsonProperty("ipv4")
  IPv4("ipv4"),

  @JsonProperty("ipv6")
  IPv6("ipv6"),

  @JsonProperty("credentials")
  Credentials("credentials"),

  @JsonProperty("cve")
  CVE("cve"),

  @Hidden
  @JsonProperty("asset")
  Asset("asset");

  private final String label;

  ContractOutputType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
