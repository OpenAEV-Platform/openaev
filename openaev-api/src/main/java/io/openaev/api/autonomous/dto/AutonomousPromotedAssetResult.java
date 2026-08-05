package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result of promoting an autonomous-run finding into a real, targetable asset. The original finding
 * is kept and linked to the new asset; the orchestrator uses {@code asset_id} as the target of the
 * next chained step to pivot from "I discovered host X" to "I am now attacking host X".
 */
@Getter
@AllArgsConstructor
@Schema(description = "Result of promoting a finding to a targetable asset")
public class AutonomousPromotedAssetResult {

  @JsonProperty("asset_id")
  @Schema(description = "Id of the created (endpoint) asset - use it as an inject target")
  private String assetId;

  @JsonProperty("asset_name")
  @Schema(description = "Name of the created asset")
  private String assetName;

  @JsonProperty("finding_id")
  @Schema(description = "Id of the original finding (kept, now linked to the asset)")
  private String findingId;
}
