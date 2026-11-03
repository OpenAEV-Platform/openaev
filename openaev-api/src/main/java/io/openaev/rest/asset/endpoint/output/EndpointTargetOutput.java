package io.openaev.rest.asset.endpoint.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.asset.endpoint.form.AgentOutput;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
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
  @JsonProperty("asset_hostname")
  private String hostname;

  @Schema(description = "List IPs")
  @JsonProperty("asset_ips")
  private Set<String> ips;

  @Schema(description = "Seen IP")
  @JsonProperty("asset_seen_ip")
  private String seenIp;

  @Schema(description = "List agents installed")
  @JsonProperty("asset_agents")
  private Set<AgentOutput> agents;

  @Schema(
      description =
          "List of asset groups this asset was targeted through. Empty for an asset targeted directly.")
  @JsonProperty("asset_groups")
  private Set<AssetGroupSimple> assetGroups;
}
