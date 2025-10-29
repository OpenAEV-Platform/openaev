package io.openaev.rest.team;

import static io.openaev.database.specification.TeamSpecification.*;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Team;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.TeamService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ScenarioTeamApi extends RestBehavior {

  private final TeamService teamService;

  @PostMapping(SCENARIO_URI + "/{scenarioId}/teams/search")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Page<TeamOutput> teams(
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @RequestParam
          @Schema(
              description =
                  "Controls which teams to retrieve - true: Only teams that are part of the scenario")
          final boolean contextualOnly) {
    Specification<Team> teamSpecification;
    if (!contextualOnly) {
      teamSpecification = contextual(false).or(fromScenario(scenarioId));
      // contextual(false) => Teams that exist independently, not created from a specific context
      // (scenario or simulation)
    } else {
      teamSpecification = fromScenario(scenarioId);
    }
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }
}
