package io.openaev.rest.asset_group.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Filters.FilterGroup;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetGroupInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("asset_group_name")
  private String name;

  @JsonProperty("asset_group_description")
  private String description;

  @JsonProperty("asset_group_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("asset_group_dynamic_filter")
  private FilterGroup dynamicFilter = FilterGroup.defaultFilterGroup();
}
