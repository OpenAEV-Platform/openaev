package io.openaev.service.expectation;

import static io.openaev.service.InjectExpectationUtils.*;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.addResult;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationResultRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.utils.ExpectationUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared behavior for table-top style expectations (manual/challenge/article). */
public abstract class AbstractTableTopBehavior
    extends ExpectationBehavior<TableTopInjectExpectation> {

  @Resource protected ExpectationPropertiesConfig expectationPropertiesConfig;

  protected final InjectExpectationRepository injectExpectationRepository;

  protected AbstractTableTopBehavior(
      InjectExpectationRepository injectExpectationRepository,
      InjectExpectationResultRepository injectExpectationResultRepository) {
    super(injectExpectationResultRepository);
    this.injectExpectationRepository = injectExpectationRepository;
  }

  /** Returns the default result entry for player-level expectations. */
  protected abstract InjectExpectationResult buildDefaultPlayerResult(Double expectedScore);

  // -- INITIALIZE AND SAVE --

  /**
   * Creates and persists expectations for each team/player target. Results are set on player-level
   * expectations only.
   */
  @Override
  public void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject,
      TableTopInjectExpectation expectationTemplate,
      @Nullable String implantType) {

    if (!shouldInitializeExpectation(executableInject)) {
      return;
    }
    boolean isAtomicTesting = executableInject.getInjection().getInject().isAtomicTesting();

    List<Team> teams = executableInject.getTeams();
    if (teams.isEmpty()) {
      return;
    }

    List<TableTopInjectExpectation> templates =
        expandTemplatesForContext(executableInject, expectationTemplate);

    List<TableTopInjectExpectation> allExpectations = new ArrayList<>();

    for (TableTopInjectExpectation template : templates) {
      if (isAtomicTesting) {
        buildExpectationsForAtomicTesting(template, teams, allExpectations);
      } else {
        buildExpectationsForExerciseInject(executableInject, template, teams, allExpectations);
      }
    }

    allExpectations.forEach(this::initializeResults);
    recomputeScores(expectationTemplate.getInject(), allExpectations);
    injectExpectationRepository.saveAll(allExpectations);
  }

  /**
   * Recomputes the scores of the new expectations from their default results. A default result may
   * already settle a player (phishing starts as "resisted"): the player and its team must then be
   * scored from the start, as the expiration collector fails every expectation without a score.
   */
  private void recomputeScores(Inject inject, List<TableTopInjectExpectation> expectations) {
    // The parents are resolved from the inject expectations: register the new ones first
    inject.getExpectations().addAll(expectations);
    expectations.forEach(expectation -> expectation.setScore(computeLeafScore(expectation)));
    expectations.stream()
        .filter(expectation -> !isPlayerExpectation(expectation))
        .forEach(this::recomputeParentScores);
  }

  /**
   * Expands a single template into several context-specific templates before target multiplication.
   *
   * <p>Default behavior returns the template unchanged. Behaviors whose form expectation maps to
   * several domain entities (e.g. one expectation per challenge or per article) override this to
   * clone the template and attach each entity.
   *
   * @param executableInject the executable inject providing the content and resolved context
   * @param template the untargeted template produced by {@link
   *     #convertFormExpectationToBaseInjectExpectation}
   * @return one template per context entity (at least one)
   */
  protected List<TableTopInjectExpectation> expandTemplatesForContext(
      ExecutableInject executableInject, TableTopInjectExpectation template) {
    return List.of(template);
  }

  /**
   * Builds expectations for atomic testing: one team-level and one player-level expectation per
   * team/user combination.
   */
  private void buildExpectationsForAtomicTesting(
      TableTopInjectExpectation template,
      List<Team> teams,
      List<TableTopInjectExpectation> allExpectations) {
    teams.forEach(
        team -> {
          allExpectations.add(buildExpectationForTarget(template, team, null));

          team.getUsers()
              .forEach(
                  user -> {
                    allExpectations.add(buildExpectationForTarget(template, team, user));
                  });
        });
  }

  /**
   * Builds expectations for exercise injects: player-level expectations for enabled players only,
   * with team-level expectations only for teams that have at least one enabled player.
   */
  private void buildExpectationsForExerciseInject(
      ExecutableInject executableInject,
      TableTopInjectExpectation template,
      List<Team> teams,
      List<TableTopInjectExpectation> allExpectations) {
    String exerciseId = executableInject.getInjection().getExercise().getId();

    List<TableTopInjectExpectation> playerExpectations = new ArrayList<>();

    // Create expectations for every enabled player in every team
    for (Team team : teams) {
      team.getExerciseTeamUsers().stream()
          .filter(etu -> etu.getExercise().getId().equals(exerciseId))
          .forEach(
              etu ->
                  playerExpectations.add(buildExpectationForTarget(template, team, etu.getUser())));
    }

    // Create a set of teams that have at least one enabled player
    Set<Team> teamsWithPlayers =
        playerExpectations.stream()
            .map(TableTopInjectExpectation::getTeam)
            .collect(Collectors.toSet());

    // Add only the expectations where the team has at least one enabled player
    for (Team team : teamsWithPlayers) {
      allExpectations.add(buildExpectationForTarget(template, team, null));
    }

    allExpectations.addAll(playerExpectations);
  }

  /**
   * Clones the template expectation and sets the team/user target.
   *
   * @param template the expectation template to clone
   * @param team the target team
   * @param user the target user, or {@code null} for team-level expectations
   * @return a new expectation ready to be persisted
   */
  private static TableTopInjectExpectation buildExpectationForTarget(
      TableTopInjectExpectation template, Team team, @Nullable User user) {
    TableTopInjectExpectation expectation = template.clone();
    expectation.setTeam(team);
    expectation.setUser(user);
    return expectation;
  }

  // -- INITIALIZE RESULTS --

  /** Sets the default player result on player-level expectations only. */
  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    if (!(expectation instanceof TableTopInjectExpectation tableTop)) {
      return;
    }

    if (tableTop.getUser() != null) {
      InjectExpectationResult defaultPlayerResult =
          buildDefaultPlayerResult(expectation.getExpectedScore());
      if (defaultPlayerResult != null) {
        expectation.setResults(new ArrayList<>(List.of(defaultPlayerResult)));
      }
    }
  }

  // -- END INITIALIZE

  /**
   * Resolves to player expectations level. A team without players (e.g. an empty team targeted by
   * an atomic testing) is its own leaf, and so is an expectation without team: a legacy MANUAL
   * expectation created by a technical injector (implant, Caldera) on an agent, an asset or an
   * asset group, which is loaded as a table-top expectation by its type.
   */
  @Override
  public List<? extends BaseInjectExpectation> getLeaves(BaseInjectExpectation expectation) {
    if (!(expectation instanceof TableTopInjectExpectation tableTop)) {
      return List.of();
    }
    if (tableTop.getTeam() == null || isPlayerExpectation(tableTop)) {
      return List.of(tableTop);
    }
    List<TableTopInjectExpectation> playersExpectations =
        getPlayersExpectationsInSameContext(tableTop);
    return playersExpectations.isEmpty() ? List.of(tableTop) : playersExpectations;
  }

  /** A human response, or its expiration, replaces every previous result */
  @Override
  public void addResultToLeaf(
      BaseInjectExpectation leaf, ExpectationVerdict verdict, String resultLabel) {
    leaf.clearResults();
    addResult(leaf, verdict, resultLabel);
  }

  /**
   * A human response is the only result of the expectation, whatever its source (the player, the
   * admin overwriting it, or the team validation copied on the players by the legacy flow):
   * deleting it clears the leaf.
   */
  @Override
  public List<InjectExpectationResult> removeResultFromLeaf(
      BaseInjectExpectation leaf, String sourceId) {
    return leaf.clearResults();
  }

  /**
   * The affected parents are the teams of the given expectations, matched on team, name and
   * context, recomputed from their players. A team without players keeps its own score, as it is
   * its own leaf (see {@link #getLeaves}), and an expectation without team has no parent.
   */
  @Override
  public List<TableTopInjectExpectation> recomputeAffectedParents(
      Inject inject,
      BaseInjectExpectation.EXPECTATION_TYPE type,
      List<TableTopInjectExpectation> expectations) {
    // An inject may hold technical expectations too
    List<TableTopInjectExpectation> expectationsOfInjectWithSameType =
        inject.getExpectations().stream()
            .filter(TableTopInjectExpectation.class::isInstance)
            .map(TableTopInjectExpectation.class::cast)
            .filter(e -> type.equals(e.getType()))
            .filter(e -> e.getTeam() != null)
            .toList();

    // Affected teams: the tree (team, name, context) of each player or team
    Set<TeamTree> affectedTeamTrees =
        expectations.stream()
            .filter(e -> e.getTeam() != null)
            .map(this::teamTree)
            .collect(Collectors.toSet());

    Map<TeamTree, List<TableTopInjectExpectation>> playersByTeamTree =
        expectationsOfInjectWithSameType.stream()
            .filter(ExpectationUtils::isPlayerExpectation)
            .collect(Collectors.groupingBy(this::teamTree));

    List<TableTopInjectExpectation> updatedTeams = new ArrayList<>();
    expectationsOfInjectWithSameType.stream()
        .filter(e -> !isPlayerExpectation(e))
        .filter(team -> affectedTeamTrees.contains(teamTree(team)))
        .forEach(
            team ->
                recomputeTeamScore(team, playersByTeamTree.getOrDefault(teamTree(team), List.of()))
                    .ifPresent(updatedTeams::add));
    return updatedTeams;
  }

  /** Derives the team score from its players; a team without players keeps its own score. */
  private Optional<TableTopInjectExpectation> recomputeTeamScore(
      TableTopInjectExpectation teamExpectation,
      List<TableTopInjectExpectation> playersExpectations) {
    if (playersExpectations.isEmpty()) {
      return Optional.empty();
    }
    teamExpectation.setScore(
        computeChildrenScore(
            teamExpectation.isExpectationGroup(),
            teamExpectation.getExpectedScore(),
            playersExpectations));
    return Optional.of(teamExpectation);
  }

  // -- CONTEXT --

  /**
   * Returns the ID of the context entity of the expectation. Defaults to {@code null}, a single
   * context per team; behaviors expanding one expectation tree per entity ({@link
   * #expandTemplatesForContext}) override it, so a team and its players are matched per entity and
   * never mixed with the trees of the other entities of the inject.
   *
   * @param expectation the expectation whose context is read, of a type handled by this behavior
   */
  @Nullable
  protected String contextId(TableTopInjectExpectation expectation) {
    return null;
  }

  /**
   * The expectation tree of a team within an inject and a type: a team expectation and the
   * expectations of its players share it. The name and the context may be {@code null}.
   */
  private record TeamTree(String teamId, @Nullable String name, @Nullable String contextId) {}

  private TeamTree teamTree(TableTopInjectExpectation expectation) {
    return new TeamTree(
        expectation.getTeam().getId(), expectation.getName(), contextId(expectation));
  }

  private boolean isSameContext(
      TableTopInjectExpectation expectation, TableTopInjectExpectation reference) {
    return Objects.equals(contextId(expectation), contextId(reference));
  }

  private List<TableTopInjectExpectation> getPlayersExpectationsInSameContext(
      TableTopInjectExpectation teamExpectation) {
    return getPlayersExpectationsForTeam(teamExpectation).stream()
        .filter(playerExpectation -> isSameContext(playerExpectation, teamExpectation))
        .toList();
  }
}
