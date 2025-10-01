package io.openaev.rest.asset.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public abstract class AssetInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("asset_name")
  private String name;

  @JsonProperty("asset_description")
  private String description;

  @JsonProperty("asset_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("asset_external_reference")
  private String externalReference;
}
