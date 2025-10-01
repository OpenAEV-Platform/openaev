package io.openaev.rest.asset.endpoint.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
@JsonInclude(NON_NULL)
public class EndpointOverviewOutput {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;

  @Schema(description = "Asset name")
  @JsonProperty("asset_name")
  @NotBlank
  private String name;

  @Schema(description = "Asset description")
  @JsonProperty("asset_description")
  private String description;

  @Schema(description = "Hostname")
  @JsonProperty("endpoint_hostname")
  private String hostname;

  @Schema(description = "Platform")
  @JsonProperty("endpoint_platform")
  private Endpoint.PLATFORM_TYPE platform;

  @Schema(description = "Architecture")
  @JsonProperty("endpoint_arch")
  private Endpoint.PLATFORM_ARCH arch;

  @Schema(description = "List IPs")
  @JsonProperty("endpoint_ips")
  private Set<String> ips;

  @Schema(description = "Seen IP")
  @JsonProperty("endpoint_seen_ip")
  private String seenIp;

  @Schema(description = "List of MAC addresses")
  @JsonProperty("endpoint_mac_addresses")
  private Set<String> macAddresses;

  @Schema(description = "List of primary agents")
  @JsonProperty("asset_agents")
  @NotNull
  private Set<AgentOutput> agents;

  @Schema(description = "Tags")
  @JsonProperty("asset_tags")
  private Set<String> tags;

  @Schema(description = "True if the endpoint is in an End of Life state")
  @JsonProperty("endpoint_is_eol")
  private boolean isEol;
}
