package io.openaev.injector_contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint;
import java.util.function.Function;

public enum ContractTargetedProperty {
  @JsonProperty("hostname")
  hostname("Hostname", Endpoint::getHostname),

  @JsonProperty("seen_ip")
  seen_ip("Seen IP", Endpoint::getSeenIp),

  @JsonProperty("local_ip")
  local_ip(
      "Local IP (first)",
      (Endpoint endpoint) -> {
        String[] ips = endpoint.getIps();
        return (ips != null && ips.length > 0) ? ips[0] : null;
      });

  public final String label;
  public final Function<Endpoint, String> toEndpointValue;

  ContractTargetedProperty(String label, Function<Endpoint, String> toEndpointValue) {
    this.label = label;
    this.toEndpointValue = toEndpointValue;
  }
}
