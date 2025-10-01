package io.openaev.rest.tag_rule.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Builder(toBuilder = true)
@Getter
@Jacksonized
@EqualsAndHashCode
public class TagRuleInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("tag_name")
  @Schema(description = "Name of the tag")
  private String tagName;

  @JsonProperty("asset_groups")
  @Schema(description = "Asset groups of the tag rule")
  private List<String> assetGroups = new ArrayList<>();
}
