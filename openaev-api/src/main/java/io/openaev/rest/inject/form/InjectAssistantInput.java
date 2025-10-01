package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Schema(
    description =
        "Input model for automatically generating injects, based on the provided attack pattern IDs and target asset or asset group IDs.")
public class InjectAssistantInput {

  @JsonProperty("asset_ids")
  @Schema(
      description =
          "List of asset IDs to target. Either asset_ids or asset_group_ids must be provided.")
  private List<String> assetIds = new ArrayList<>();

  @JsonProperty("asset_group_ids")
  @Schema(
      description =
          "List of asset group IDs to target. Either asset_ids or asset_group_ids must be provided.")
  private List<String> assetGroupIds = new ArrayList<>();

  @NotEmpty
  @JsonProperty("attack_pattern_ids")
  @Schema(description = "List of attack pattern used to generate injects")
  private List<String> attackPatternIds = new ArrayList<>();

  @NotNull
  @JsonProperty("inject_by_ttp_number")
  @Schema(description = "Number of injects to generate for each TTP")
  private Integer injectByTTPNumber = 1;
}
