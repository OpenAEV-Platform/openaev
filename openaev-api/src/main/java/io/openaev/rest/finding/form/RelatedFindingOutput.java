package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.rest.inject.output.InjectSimple;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
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

  @Schema(description = "Teams linked to the finding occurrence")
  @JsonProperty("finding_teams")
  private Set<TargetSimple> teams;

  @Schema(description = "Players (persons) linked to the finding occurrence")
  @JsonProperty("finding_users")
  private Set<TargetSimple> users;
}
