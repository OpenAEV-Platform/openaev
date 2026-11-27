package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Declares a scenario a reachability assertion, or clears it. */
@Data
public class ScenarioReachabilityAssertionInput {

  @Schema(
      description =
          "True to make the scenario a reachability assertion: only read-only injects may run on its"
              + " schedule")
  @JsonProperty("scenario_reachability_assertion")
  private boolean reachabilityAssertion;
}
