package io.openaev.injector_contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint;
import java.util.function.Function;

/**
 * Enumeration of properties that can be extracted from targeted endpoints.
 *
 * <p>When targeting assets for injection, this enum defines which endpoint property should be used
 * for targeting (e.g., using hostname vs IP address). Each property includes:
 *
 * <ul>
 *   <li>A display label for the UI
 *   <li>A function to extract the property value from an Endpoint
 * </ul>
 *
 * <p>This is used with {@link io.openaev.injector_contract.fields.ContractTargetedAsset} fields to
 * allow users to select which property to use when targeting assets.
 *
 * @see Endpoint
 * @see io.openaev.injector_contract.fields.ContractTargetedAsset
 */
public enum ContractTargetedProperty {
  /** Target using the endpoint's hostname. */
  @JsonProperty("hostname")
  hostname("Hostname", Endpoint::getHostname),

  /** Target using the endpoint's externally visible IP address. */
  @JsonProperty("seen_ip")
  seen_ip("Seen IP", Endpoint::getSeenIp),

  /** Target using the endpoint's first local IP address. */
  @JsonProperty("local_ip")
  local_ip(
      "Local IP (first)",
      (Endpoint endpoint) -> {
        String[] ips = endpoint.getIps();
        return (ips != null && ips.length > 0) ? ips[0] : null;
      });

  /** Display label shown in the user interface. */
  public final String label;

  /** Function to extract the property value from an Endpoint. */
  public final Function<Endpoint, String> toEndpointValue;

  ContractTargetedProperty(String label, Function<Endpoint, String> toEndpointValue) {
    this.label = label;
    this.toEndpointValue = toEndpointValue;
  }
}
