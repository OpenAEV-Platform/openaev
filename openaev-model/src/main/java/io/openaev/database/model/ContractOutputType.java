package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum ContractOutputType {
  @JsonProperty("text") Text("text"),
  @JsonProperty("number") Number("number"),
  @JsonProperty("port") Port("port"),
  @JsonProperty("portscan") PortScan("portscan"),
  @JsonProperty("ipv4") IPv4("ipv4"),
  @JsonProperty("ipv6") IPv6("ipv6"),
  @JsonProperty("credentials") Credentials("credentials"),
  @JsonProperty("cve") CVE("cve"),
  @JsonProperty("asset") Asset("asset");

  private final String label;

  ContractOutputType(String label) {
    this.label = label;
  }
}