package io.openaev.api.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.exercise.form.ExerciseSimple;
import io.openaev.api.inject.output.InjectSimple;
import io.openaev.api.scenario.form.ScenarioSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonInclude(NON_NULL)
public class RelatedFindingOutput extends AggregatedFindingOutput {

  @Schema(description = "Inject linked to finding")
  @JsonProperty("finding_inject")
  @NotBlank
  private InjectSimple inject;

  @Schema(description = "Simulation linked to inject")
  @JsonProperty("finding_simulation")
  private ExerciseSimple simulation;

  @Schema(description = "Scenario linked to inject")
  @JsonProperty("finding_scenario")
  private ScenarioSimple scenario;
}
