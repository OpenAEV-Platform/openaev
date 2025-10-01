package io.openaev.rest.asset_group.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(NON_NULL)
public class AssetGroupSimple {

  @Schema(description = "Asset group Id")
  @JsonProperty("asset_group_id")
  @NotBlank
  private String id;

  @Schema(description = "Asset group Name")
  @JsonProperty("asset_group_name")
  @NotBlank
  private String name;
}
