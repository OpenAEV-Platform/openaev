package io.openaev.rest.asset.endpoint.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint;
import io.openaev.rest.asset.form.AssetInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class EndpointInput extends AssetInput {
  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("endpoint_platform")
  private Endpoint.PLATFORM_TYPE platform;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("endpoint_arch")
  private Endpoint.PLATFORM_ARCH arch;

  @JsonProperty("endpoint_ips")
  private String[] ips;

  @JsonProperty("endpoint_hostname")
  private String hostname;

  @JsonProperty("endpoint_agent_version")
  private String agentVersion;

  @JsonProperty("endpoint_mac_addresses")
  private String[] macAddresses;

  @Schema(description = "True if the endpoint is in an End of Life state")
  @JsonProperty("endpoint_is_eol")
  private boolean isEol;
}
