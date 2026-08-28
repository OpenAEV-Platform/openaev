package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Frozen asset composing a launched simulation's scope rule (display only).")
public class AssetSnapshotOutput {

  @Schema(description = "Frozen asset id.")
  @JsonProperty("asset_snapshot_id")
  private String id;

  @Schema(description = "Frozen asset name.")
  @JsonProperty("asset_snapshot_name")
  private String name;

  @Schema(description = "Frozen number of agents on the asset.")
  @JsonProperty("asset_snapshot_agents_count")
  private int agentsCount;

  @Schema(description = "Frozen distinct executor types of the asset's agents.")
  @JsonProperty("asset_snapshot_executors")
  private List<String> executors;
}
