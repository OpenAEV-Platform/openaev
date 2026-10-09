package io.openaev.rest.team;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.TeamSpecification.*;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawTeamIndexing;
import io.openaev.database.repository.*;
import io.openaev.rest.atomic_testing.form.InjectResultOutput;
import io.openaev.rest.exception.AlreadyExistingException;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ResourceInUseException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.helper.TeamHelper;
import io.openaev.rest.team.form.TeamBulkProcessingInput;
import io.openaev.rest.team.form.TeamCreateInput;
import io.openaev.rest.team.form.TeamUpdateInput;
import io.openaev.rest.team.form.UpdateUsersTeamInput;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.InjectSearchService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.service.account.ReservedKeyValidator;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.TransientObjectException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Propagation;
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
  private static final String TENANT_TEAM_URI = TENANT_PREFIX + "/teams";

  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final TagRepository tagRepository;
  private final TeamService teamService;
  private final UserService userService;
  private final InjectSearchService injectSearchService;
  private final TenantWriteScopeResolver writeScopeResolver;

  @LogExecutionTime
  @GetMapping({TEAM_URI, TENANT_TEAM_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of teams")})
  @Operation(summary = "List teams", description = "Return the teams")
  @Transactional
  public Iterable<TeamSimple> getTeams(TxCtx ctx) {
    Set<String> tenantIds = teamService.readScope(ctx);
    if (tenantIds.isEmpty()) {
      return List.of();
    }
    // We get all the teams as raw
    List<RawTeamIndexing> teams = fromIterable(teamRepository.rawTeams(tenantIds));

    return TeamHelper.rawAllTeamToSimplerAllTeam(teams);
  }

  @LogExecutionTime
  @PostMapping({"/api/teams/search", TENANT_TEAM_URI + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  @Transactional(readOnly = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of teams")})
  @Operation(
      summary = "Search teams",
      description = "Search the teams corresponding to the criteria")
  public Page<TeamOutput> searchTeams(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    final Specification<Team> teamSpecification = contextual(false);
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }

  @LogExecutionTime
  @PostMapping({"/api/teams/find", TENANT_TEAM_URI + "/find"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  @Transactional(readOnly = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of teams")})
  @Operation(description = "Find a list of teams based on their ids", summary = "Find teams")
  public List<TeamOutput> findTeams(
      TxCtx ctx, @RequestBody @Valid @NotNull final List<String> teamIds) {
    return this.teamService.find(fromIds(teamIds));
  }

  /**
   * "Injects played" for the team detail page: every inject (atomic testing or simulation inject)
   * that concerns this team, whether it was targeted directly or evidenced by the table-top
   * expectations persisted at execution time. This matches the scope of the team expectation
   * counters, unlike the plain atomic-testing search which only sees direct targeting of standalone
   * injects.
   */
  @LogExecutionTime
  @PostMapping({
    TEAM_URI + "/{teamId}/injects/search",
    TENANT_TEAM_URI + "/{teamId}/injects/search"
  })
  @AccessControl(
      resourceId = "#teamId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.TEAM)
  @Transactional(readOnly = true)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The injects played by the team")})
  @Operation(
      summary = "Search injects played by team",
      description =
          "Search every inject that concerns the team (direct targeting or execution evidence)")
  public Page<InjectResultOutput> searchInjectsForTeam(
      TxCtx ctx,
      @PathVariable @NotBlank final String teamId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return injectSearchService.getPageOfInjectResultsForTeam(teamId, searchPaginationInput);
  }

  @GetMapping({"/api/teams/{teamId}", TENANT_TEAM_URI + "/{teamId}"})
  @AccessControl(
      resourceId = "#teamId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The team")})
  @Operation(description = "Get a team", summary = "Get team")
  @Transactional
  public Team getTeam(
      TxCtx ctx, @PathVariable @Schema(description = "ID of the team") String teamId) {
    return teamService.teamInScope(ctx, teamId);
  }

  @GetMapping({"/api/teams/{teamId}/players", TENANT_TEAM_URI + "/{teamId}/players"})
  @AccessControl(
      resourceId = "#teamId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.TEAM)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The list of players of the team")})
  @Operation(description = "Get the list of players of a team", summary = "Get team's players")
  @Transactional
  public Iterable<User> getTeamPlayers(
      TxCtx ctx, @PathVariable @Schema(description = "ID of the team") String teamId) {
    return teamService.teamInScope(ctx, teamId).getUsers();
  }

  @PostMapping({TEAM_URI, TENANT_TEAM_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.TEAM)
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The created team")})
  @Operation(description = "Create a new team", summary = "Create team")
  public Team createTeam(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody TeamCreateInput input) {
    String writeTenant = writeScopeResolver.tenantForWrite(ctx, null);
    isTeamAlreadyExists(writeTenant, input);
    Team team = new Team();
    team.setTenant(new Tenant(writeTenant));
    team.setUpdateAttributes(input);
    team.setOrganization(
        updateRelation(input.getOrganizationId(), team.getOrganization(), organizationRepository));
    team.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    team.setExercises(iterableToSet(exerciseRepository.findAllById(input.getExerciseIds())));
    team.setScenarios(iterableToSet(scenarioRepository.findAllById(input.getScenarioIds())));
    return teamRepository.save(team);
  }

  @PostMapping({"/api/teams/upsert", TENANT_TEAM_URI + "/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.TEAM)
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The created/updated team")})
  @Operation(description = "Create a new team or update an existing team", summary = "Upsert team")
  public Team upsertTeam(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody TeamCreateInput input) {
    if (input.getContextual() && input.getExerciseIds().toArray().length > 1) {
      throw new UnsupportedOperationException(
          "Contextual team can only be associated to one exercise");
    }
    String writeTenant = writeScopeResolver.tenantForWrite(ctx, null);
    Optional<Team> team =
        teamRepository.findByNameAndTenantIdIn(input.getName(), Set.of(writeTenant));
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
      newTeam.setTenant(new Tenant(writeTenant));
      newTeam.setUpdateAttributes(input);
      newTeam.setOrganization(
          updateRelation(
              input.getOrganizationId(), newTeam.getOrganization(), organizationRepository));
      newTeam.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
      newTeam.setExercises(iterableToSet(exerciseRepository.findAllById(input.getExerciseIds())));
      newTeam.setScenarios(iterableToSet(scenarioRepository.findAllById(input.getScenarioIds())));
      return teamRepository.save(newTeam);
    }
  }

  @DeleteMapping({"/api/teams/{teamId}", TENANT_TEAM_URI + "/{teamId}"})
  @AccessControl(
      resourceId = "#teamId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200")})
  @Operation(description = "Delete an existing team", summary = "Delete team")
  @Transactional
  public void deleteTeam(
      TxCtx ctx, @PathVariable @Schema(description = "ID of the team") String teamId)
      throws ResourceInUseException {
    Team team = teamService.teamInScope(ctx, teamId);
    try {
      teamService.deleteAllDetachingInjects(List.of(team));
    } catch (InvalidDataAccessApiUsageException | TransientObjectException ex) {
      throw new ResourceInUseException(
          "Cannot delete this team because it is still in use. Please remove its dependencies first.",
          ex);
    }
  }

  @LogExecutionTime
  @DeleteMapping({TEAM_URI, TENANT_TEAM_URI})
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.TEAM)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The ids of the deleted teams")})
  @Operation(description = "Bulk delete of teams", summary = "Bulk delete teams")
  // SUPPORTS (not REQUIRED): the service deletes in small independent chunk transactions with
  // deadlock retry; a request-wide transaction would force everything back into one transaction.
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<String> bulkDeleteTeams(
      TxCtx ctx, @RequestBody @Valid final TeamBulkProcessingInput input)
      throws ResourceInUseException {
    return this.teamService.bulkDelete(ctx, input);
  }

  @PutMapping({"/api/teams/{teamId}", TENANT_TEAM_URI + "/{teamId}"})
  @Transactional
  @AccessControl(
      resourceId = "#teamId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated team")})
  @Operation(description = "Update an existing team", summary = "Update team")
  public Team updateTeam(
      TxCtx ctx,
      @PathVariable @Schema(description = "ID of the team") String teamId,
      @Valid @RequestBody TeamUpdateInput input) {
    Team team = teamService.teamInScope(ctx, teamId);
    team.setUpdateAttributes(input);
    team.setUpdatedAt(now());
    team.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    team.setOrganization(
        updateRelation(input.getOrganizationId(), team.getOrganization(), organizationRepository));
    return teamRepository.save(team);
  }

  @PutMapping({"/api/teams/{teamId}/players", TENANT_TEAM_URI + "/{teamId}/players"})
  @Transactional
  @AccessControl(
      resourceId = "#teamId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.TEAM)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated team")})
  @Operation(
      description = "Update the list of users of a team team",
      summary = "Update team players")
  public Team updateTeamUsers(
      TxCtx ctx,
      @PathVariable @Schema(description = "ID of the team") String teamId,
      @Valid @RequestBody UpdateUsersTeamInput input) {
    Team team = teamService.teamInScope(ctx, teamId);
    List<String> nextUserIds =
        input.getUserIds() == null ? Collections.emptyList() : input.getUserIds();
    Iterable<User> teamUsers = userRepository.findAllById(nextUserIds);
    // Reserved service/connector accounts are system users, never players: silently drop them so
    // team membership stays consistent with the player lists that hide them.
    List<User> nextTeamUsers = ReservedKeyValidator.excludeReservedUsers(teamUsers);
    List<String> nextTeamUserIds = nextTeamUsers.stream().map(User::getId).toList();
    List<String> removedUserIds =
        team.getUsers().stream()
            .map(User::getId)
            .filter(existingUserId -> !nextTeamUserIds.contains(existingUserId))
            .toList();

    teamService.removeUsersFromTeamActivations(teamId, removedUserIds);

    // The deletes above clear the persistence context, so the team loaded before them is stale:
    // saving it would merge its obsolete exerciseTeamUsers collection (cascade = ALL) and
    // re-insert the audience rows just deleted. Reload it so the entity matches the database.
    Team refreshedTeam = removedUserIds.isEmpty() ? team : teamService.teamInScope(ctx, teamId);
    refreshedTeam.setUsers(nextTeamUsers);
    return teamRepository.save(refreshedTeam);
  }

  // -- OPTION --
  @GetMapping({TEAM_URI + "/options", TENANT_TEAM_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  public List<FilterUtilsJpa.Option> optionsByName(
      TxCtx ctx,
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId,
      @RequestParam(required = false) final String inputFilterOption) {
    List<FilterUtilsJpa.Option> options = List.of();
    Set<String> tenantIds = teamService.readScope(ctx);
    if (tenantIds.isEmpty()) {
      return options;
    }
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
              teamRepository
                  .findAllTeamsForAtomicTestingsSimulationsAndScenarios(tenantIds)
                  .stream()
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
                      StringUtils.trimToNull(sourceId),
                      StringUtils.trimToNull(searchText),
                      tenantIds)
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
    }
    return options;
  }

  @PostMapping({TEAM_URI + "/options", TENANT_TEAM_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.TEAM)
  public List<FilterUtilsJpa.Option> optionsById(TxCtx ctx, @RequestBody final List<String> ids) {
    return fromIterable(this.teamRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  // -- PRIVATE --

  /**
   * Uniqueness is per tenant, so the pre-check runs in the tenant the row is about to be written
   * to, never in the caller's whole read scope: a name taken in another of the caller's tenants
   * must not refuse a create here.
   */
  private void isTeamAlreadyExists(
      @NotNull final String writeTenant, @NotNull final TeamCreateInput input) {
    List<Team> teams =
        this.teamRepository.findAllByNameIgnoreCaseAndTenantIdIn(
            input.getName(), Set.of(writeTenant));
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
