package io.openaev.rest.asset.endpoint.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.asset.endpoint.form.AgentOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class EndpointTargetOutput {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;

  @Schema(description = "Hostname")
  @JsonProperty("endpoint_hostname")
  private String hostname;

  @Schema(description = "List IPs")
  @JsonProperty("endpoint_ips")
  private Set<String> ips;

  @Schema(description = "Seen IP")
  @JsonProperty("endpoint_seen_ip")
  private String seenIp;

  @Schema(description = "List agents installed")
  @JsonProperty("asset_agents")
  private Set<AgentOutput> agents;
}
