package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ScenarioAssetGroupOrderInput {

  /** Asset group ids, first tested first. An empty list clears the order. */
  @NotNull
  @JsonProperty("scenario_asset_group_order")
  private List<String> assetGroupIds = new ArrayList<>();
}
