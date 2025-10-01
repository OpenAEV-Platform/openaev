package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
public class CheckScenarioRulesInput {
  @JsonProperty("new_tags")
  @Schema(description = "List of tag that will be applied to the scenario")
  List<String> newTags;
}
