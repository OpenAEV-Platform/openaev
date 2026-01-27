package io.openaev.rest.team;

import static io.openaev.database.specification.TeamSpecification.*;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawTeam;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.AlreadyExistingException;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.helper.TeamHelper;
import io.openaev.rest.team.form.TeamCreateInput;
import io.openaev.rest.team.form.TeamUpdateInput;
import io.openaev.rest.team.form.UpdateUsersTeamInput;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InputFilterOptions;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@UserRoleDescription
@Slf4j
@Tag(
    name = "Teams management",
    description = "Endpoints to manage teams",
    externalDocs =
        @ExternalDocumentation(
            description = "Documentation about teams",
            url =
                "https://docs.openaev.io/latest/usage/teams_and_players_and_organizations/#teams"))
public class TeamApi extends RestBehavior {

  public static final String TEAM_URI = "/api/teams";

  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final TagRepository tagRepository;
  private final TeamService teamService;
  private final UserService userService;

  @LogExecutionTime
  @GetMapping(TEAM_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of teams")})
  @Operation(summary = "List teams", description = "Return the teams")
  public Iterable<TeamSimple> getTeams() {
    List<RawTeam> teams;
    // We get all the teams as raw
    teams = fromIterable(teamRepository.rawTeams());

    return TeamHelper.rawAllTeamToSimplerAllTeam(teams);
  }

  @LogExecutionTime
  @PostMapping("/api/teams/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  @Transactional(readOnly = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of teams")})
  @Operation(
      summary = "Search teams",
      description = "Search the teams corresponding to the criteria")
  public Page<TeamOutput> searchTeams(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    final Specification<Team> teamSpecification = contextual(false);
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }

  @LogExecutionTime
  @PostMapping("/api/teams/find")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  @Transactional(readOnly = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of teams")})
  @Operation(description = "Find a list of teams based on their ids", summary = "Find teams")
  public List<TeamOutput> findTeams(@RequestBody @Valid @NotNull final List<String> teamIds) {
    return this.teamService.find(fromIds(teamIds));
  }

  @GetMapping("/api/teams/{teamId}")
  @RBAC(resourceId = "#teamId", actionPerformed = Action.READ, resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The team")})
  @Operation(description = "Get a team", summary = "Get team")
  public Team getTeam(@PathVariable @Schema(description = "ID of the team") String teamId) {
    return teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping("/api/teams/{teamId}/players")
  @RBAC(resourceId = "#teamId", actionPerformed = Action.READ, resourceType = ResourceType.TEAM)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The list of players of the team")})
  @Operation(description = "Get the list of players of a team", summary = "Get team's players")
  public Iterable<User> getTeamPlayers(
      @PathVariable @Schema(description = "ID of the team") String teamId) {
    return teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new).getUsers();
  }

  @PostMapping(TEAM_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.TEAM)
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The created team")})
  @Operation(description = "Create a new team", summary = "Create team")
  public Team createTeam(@Valid @RequestBody TeamCreateInput input) {
    isTeamAlreadyExists(input);
    Team team = new Team();
    team.setUpdateAttributes(input);
    team.setOrganization(
        updateRelation(input.getOrganizationId(), team.getOrganization(), organizationRepository));
    team.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    team.setExercises(fromIterable(exerciseRepository.findAllById(input.getExerciseIds())));
    team.setScenarios(fromIterable(scenarioRepository.findAllById(input.getScenarioIds())));
    return teamRepository.save(team);
  }

  @PostMapping("/api/teams/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.TEAM)
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The created/updated team")})
  @Operation(description = "Create a new team or update an existing team", summary = "Upsert team")
  public Team upsertTeam(@Valid @RequestBody TeamCreateInput input) {
    if (input.getContextual() && input.getExerciseIds().toArray().length > 1) {
      throw new UnsupportedOperationException(
          "Contextual team can only be associated to one exercise");
    }
    Optional<Team> team = teamRepository.findByName(input.getName());
    if (team.isPresent()) {
      Team existingTeam = team.get();
      existingTeam.setUpdateAttributes(input);
      existingTeam.setUpdatedAt(now());
      existingTeam.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
      existingTeam.setOrganization(
          updateRelation(
              input.getOrganizationId(), existingTeam.getOrganization(), organizationRepository));
      return teamRepository.save(existingTeam);
    } else {
      Team newTeam = new Team();
      newTeam.setUpdateAttributes(input);
      newTeam.setOrganization(
          updateRelation(
              input.getOrganizationId(), newTeam.getOrganization(), organizationRepository));
      newTeam.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
      newTeam.setExercises(fromIterable(exerciseRepository.findAllById(input.getExerciseIds())));
      newTeam.setScenarios(fromIterable(scenarioRepository.findAllById(input.getScenarioIds())));
      return teamRepository.save(newTeam);
    }
  }

  @DeleteMapping("/api/teams/{teamId}")
  @RBAC(resourceId = "#teamId", actionPerformed = Action.DELETE, resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200")})
  @Operation(description = "Delete an existing team", summary = "Delete team")
  public void deleteTeam(@PathVariable @Schema(description = "ID of the team") String teamId) {
    teamRepository.deleteById(teamId);
  }

  @PutMapping("/api/teams/{teamId}")
  @RBAC(resourceId = "#teamId", actionPerformed = Action.WRITE, resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated team")})
  @Operation(description = "Update an existing team", summary = "Update team")
  public Team updateTeam(
      @PathVariable @Schema(description = "ID of the team") String teamId,
      @Valid @RequestBody TeamUpdateInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    team.setUpdateAttributes(input);
    team.setUpdatedAt(now());
    team.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    team.setOrganization(
        updateRelation(input.getOrganizationId(), team.getOrganization(), organizationRepository));
    return teamRepository.save(team);
  }

  @PutMapping("/api/teams/{teamId}/players")
  @RBAC(resourceId = "#teamId", actionPerformed = Action.WRITE, resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated team")})
  @Operation(
      description = "Update the list of users of a team team",
      summary = "Update team players")
  public Team updateTeamUsers(
      @PathVariable @Schema(description = "ID of the team") String teamId,
      @Valid @RequestBody UpdateUsersTeamInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getUserIds());
    team.setUsers(fromIterable(teamUsers));
    return teamRepository.save(team);
  }

  // -- OPTION --
  @GetMapping(TEAM_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId,
      @RequestParam(required = false) final String inputFilterOption) {
    List<FilterUtilsJpa.Option> options = List.of();
    InputFilterOptions injectFilterOptionEnum;
    try {
      injectFilterOptionEnum = InputFilterOptions.valueOf(inputFilterOption);
    } catch (Exception e) {
      if (StringUtils.isEmpty(inputFilterOption)) {
        log.warn("InputFilterOption is null, fall back to backwards compatible case");
        if (StringUtils.isNotEmpty(sourceId)) {
          injectFilterOptionEnum = InputFilterOptions.SIMULATION_OR_SCENARIO;
        } else {
          injectFilterOptionEnum = InputFilterOptions.ATOMIC_TESTING;
        }
      } else {
        throw new BadRequestException(
            String.format("Invalid input filter option %s", inputFilterOption));
      }
    }
    switch (injectFilterOptionEnum) {
      case ALL_INJECTS:
        {
          options =
              teamRepository.findAllTeamsForAtomicTestingsSimulationsAndScenarios().stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
      case SIMULATION_OR_SCENARIO:
        {
          if (StringUtils.isEmpty(sourceId)) {
            throw new BadRequestException("Missing simulation or scenario id");
          }
          // fall through intentional
        }
      case ATOMIC_TESTING:
        {
          options =
              teamRepository
                  .findAllBySimulationOrScenarioIdAndName(
                      StringUtils.trimToNull(sourceId), StringUtils.trimToNull(searchText))
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
    }
    return options;
  }

  @PostMapping(TEAM_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.teamRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  // -- PRIVATE --

  private void isTeamAlreadyExists(@NotNull final TeamCreateInput input) {
    List<Team> teams = this.teamRepository.findAllByNameIgnoreCase(input.getName());
    if (teams.isEmpty()) {
      return;
    }

    if (FALSE.equals(input.getContextual())
        && teams.stream().anyMatch(t -> FALSE.equals(t.getContextual()))) {
      throw new AlreadyExistingException(
          "Global teams (non contextual) cannot have the same name (already exists)");
    }
    if (TRUE.equals(input.getContextual())) {
      String exerciseId = input.getExerciseIds().stream().findFirst().orElse(null);
      if (hasText(exerciseId)
          && teams.stream()
              .anyMatch(
                  t ->
                      TRUE.equals(t.getContextual())
                          && t.getExercises().stream()
                              .anyMatch((e) -> exerciseId.equals(e.getId())))) {
        throw new AlreadyExistingException(
            "A contextual team with the same name already exists on this simulation");
      }
      String scenarioId = input.getScenarioIds().stream().findFirst().orElse(null);
      if (hasText(scenarioId)
          && teams.stream()
              .anyMatch(
                  t ->
                      TRUE.equals(t.getContextual())
                          && t.getScenarios().stream()
                              .anyMatch((e) -> scenarioId.equals(e.getId())))) {
        throw new AlreadyExistingException(
            "A contextual team with the same name already exists on this scenario");
      }
    }
  }
}
