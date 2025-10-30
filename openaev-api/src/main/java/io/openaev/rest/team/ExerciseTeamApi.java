package io.openaev.rest.team;

import static io.openaev.database.specification.TeamSpecification.contextual;
import static io.openaev.database.specification.TeamSpecification.fromExercise;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Team;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.team.TeamQueryHelper.TeamQueryField;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.TeamService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ExerciseTeamApi extends RestBehavior {

  private final TeamService teamService;

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/{exerciseId}/teams/search")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Page<TeamOutput> searchTeams(
      @PathVariable @NotBlank final String exerciseId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @RequestParam
          @Schema(
              description =
                  "Controls which teams to retrieve - true: Only teams that are part of the simulation")
          final boolean contextualOnly,
      @RequestParam(required = false) String include) {
    EnumSet<TeamQueryField> includes = parseIncludes(include);
    Specification<Team> teamSpecification;
    if (!contextualOnly) {
      teamSpecification = contextual(false).or(fromExercise(exerciseId));
      // contextual(false) => Teams that exist independently, not created from a specific context
      // (scenario or simulation)
    } else {
      teamSpecification = fromExercise(exerciseId);
    }
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification, includes);
  }

  private static EnumSet<TeamQueryField> parseIncludes(@Nullable final String include) {
    if (include == null || include.isBlank()) return EnumSet.noneOf(TeamQueryField.class);
    return Arrays.stream(include.split(","))
        .map(TeamQueryField::fromString)
        .flatMap(Optional::stream)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(TeamQueryField.class)));
  }
}
