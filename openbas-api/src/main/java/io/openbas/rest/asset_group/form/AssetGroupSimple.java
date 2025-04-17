package io.openbas.rest.asset_group.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbas.database.model.Filters;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AssetGroupSimple {

  @JsonProperty("asset_group_id")
  @NotBlank
  private String id;

  @JsonProperty("asset_group_name")
  @NotBlank
  private String name;

  @JsonProperty("asset_group_dynamic_filter")
  private Filters.FilterGroup dynamicFilter;

  @JsonProperty("asset_group_tags")
  private Set<String> tags;
}
