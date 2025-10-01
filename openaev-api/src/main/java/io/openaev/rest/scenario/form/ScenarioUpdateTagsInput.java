package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ScenarioUpdateTagsInput {

  @JsonProperty("scenario_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("apply_tag_rule")
  private boolean applyTagRule = false;
}
