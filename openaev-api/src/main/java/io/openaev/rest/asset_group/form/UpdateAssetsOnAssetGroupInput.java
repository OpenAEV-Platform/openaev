package io.openaev.rest.asset_group.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAssetsOnAssetGroupInput {

  @JsonProperty("asset_group_assets")
  private List<String> assetIds;
}
