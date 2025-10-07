package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GetScenariosInput {
  @JsonProperty("scenario_ids")
  private List<String> scenarioIds = new ArrayList<>();
}
