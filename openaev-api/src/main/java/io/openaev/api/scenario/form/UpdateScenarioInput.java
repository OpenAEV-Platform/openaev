package io.openaev.api.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class UpdateScenarioInput extends ScenarioInput {
  @JsonProperty("apply_tag_rule")
  private boolean applyTagRule = false;
}
