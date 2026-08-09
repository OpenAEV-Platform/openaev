package io.openaev.service.expectation;

import static io.openaev.service.InjectExpectationUtils.*;
import static io.openaev.utils.ExpectationUtils.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/** Shared behavior for table-top style expectations (manual/challenge/article). */
@RequiredArgsConstructor
public abstract class AbstractTableTopBehavior
    implements ExpectationBehavior<TableTopInjectExpectation> {

  protected final InjectExpectationRepository injectExpectationRepository;

  /** Returns the default result entry for player-level expectations. */
  protected abstract InjectExpectationResult buildDefaultPlayerResult();

  // -- INITIALIZE AND SAVE --

  /**
   * Creates and persists expectations for each team/player target. Results are set on player-level
   * expectations only via {@link #buildDefaultPlayerResult()}.
   */
  @Override
  public void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject,
      TableTopInjectExpectation expectationTemplate,
      @Nullable String implantType) {

    boolean isAtomicTesting = executableInject.getInjection().getInject().isAtomicTesting();
    boolean isExerciseInject = !executableInject.isDirect();
    boolean isChainingExecution = executableInject.isChainingExecution();
    if (!isExerciseInject && !isAtomicTesting && !isChainingExecution) {
      return;
    }

    List<Team> teams = executableInject.getTeams();
    if (teams.isEmpty()) {
      return;
    }

    List<TableTopInjectExpectation> allExpectations = new ArrayList<>();

    if (isAtomicTesting) {
      buildExpectationsForAtomicTesting(expectationTemplate, teams, allExpectations);
    } else {
      buildExpectationsForExerciseInject(
          executableInject, expectationTemplate, teams, allExpectations);
    }

    allExpectations.forEach(this::initializeResults);
    injectExpectationRepository.saveAll(allExpectations);
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

  /** {@inheritDoc} Sets the default player result on player-level expectations only. */
  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    if (!(expectation instanceof TableTopInjectExpectation tableTop)) {
      return;
    }

    if (tableTop.getUser() != null) {
      InjectExpectationResult defaultPlayerResult = buildDefaultPlayerResult();
      if (defaultPlayerResult != null) {
        expectation.setResults(new ArrayList<>(List.of(defaultPlayerResult)));
      }
    }
  }

  // -- END INITIALIZE

  /** {@inheritDoc} Resolves to player expectations level. */
  @Override
  public List<? extends BaseInjectExpectation> getLeaves(BaseInjectExpectation expectation) {
    if (!(expectation instanceof TableTopInjectExpectation tableTop)) {
      return List.of();
    }
    if (isPlayerExpectation(tableTop)) {
      return List.of(tableTop);
    }
    return getPlayersExpectationsForTeam(tableTop);
  }

  /** {@inheritDoc} Recomputes team-level scores from their player expectations. */
  @Override
  public List<? extends BaseInjectExpectation> recomputeParentScores(
      BaseInjectExpectation expectation) {
    if (!(expectation instanceof TableTopInjectExpectation tableTopInjectExpectation)) {
      return List.of();
    }

    List<TableTopInjectExpectation> expectationForTeams =
        getTeamsExpectations(tableTopInjectExpectation);

    expectationForTeams.forEach(
        teamExpectation -> {
          List<TableTopInjectExpectation> playersExpectations =
              getPlayersExpectationsForTeam(teamExpectation);
          Double score =
              computeChildrenScore(
                  teamExpectation.isExpectationGroup(),
                  teamExpectation.getExpectedScore(),
                  playersExpectations);
          teamExpectation.setScore(score);
        });

    return expectationForTeams;
  }
}
