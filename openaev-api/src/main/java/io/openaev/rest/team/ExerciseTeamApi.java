package io.openaev.rest.team;

import static io.openaev.database.specification.TeamSpecification.contextual;
import static io.openaev.database.specification.TeamSpecification.fromExercise;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Team;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.TeamService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ExerciseTeamApi extends RestBehavior {

  private final TeamService teamService;

  @PostMapping(EXERCISE_URI + "/{exerciseId}/teams/search")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Page<TeamOutput> searchTeams(
      @PathVariable @NotBlank final String exerciseId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @RequestParam final boolean contextualOnly) {
    Specification<Team> teamSpecification;
    if (!contextualOnly) {
      teamSpecification = contextual(false).or(fromExercise(exerciseId).and(contextual(true)));
    } else {
      teamSpecification = fromExercise(exerciseId);
    }
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }
}
