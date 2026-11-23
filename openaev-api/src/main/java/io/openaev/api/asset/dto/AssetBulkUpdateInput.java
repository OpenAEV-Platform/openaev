package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.rest.asset.form.AssetBulkProcessingInput;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Bulk selection scope ({@link AssetBulkProcessingInput}: explicit ids, or the inventory search
 * input with exclusions) plus the attributes to set on every selected asset. A null attribute is
 * left unchanged; tags are added, never replaced.
 */
@Getter
@Setter
public class AssetBulkUpdateInput extends AssetBulkProcessingInput {

  @JsonProperty("asset_category")
  @Schema(types = {"string", "null"})
  private AssetCategory category;

  @JsonProperty("asset_subcategory")
  @Schema(
      types = {"string", "null"},
      description = "Must belong to asset_category; requires asset_category")
  private AssetSubCategory subcategory;

  @JsonProperty("asset_url")
  @Schema(types = {"string", "null"})
  private String url;

  @JsonProperty("asset_criticality")
  @Schema(types = {"string", "null"})
  private AssetCriticality criticality;

  @JsonProperty("asset_tags_to_add")
  @Schema(description = "Tag ids added to every selected asset (existing tags are kept)")
  private List<String> tagIdsToAdd;
}
