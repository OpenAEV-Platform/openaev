package io.openaev.rest.tag_rule.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode
public class TagRuleOutput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("tag_rule_id")
  @Schema(description = "ID of the tag rule")
  private String id;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("tag_name")
  @Schema(description = "Name of the tag associated with the tag rule")
  private String tagName;

  @JsonProperty("asset_groups")
  @Schema(description = "Asset groups of the tag rule")
  Map<String, String> assetGroups = new HashMap<>();
}
