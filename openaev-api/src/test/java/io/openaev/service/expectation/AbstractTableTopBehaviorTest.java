package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.MEDIA_PRESSURE_SOURCE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
class AbstractTableTopBehaviorTest extends IntegrationTest {

  @Autowired private ArticleBehavior articleBehavior;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectExpectationComposer expectationComposer;

  @BeforeEach
  void setUp() {
    teamComposer.reset();
    userComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    expectationComposer.reset();
  }

  @Nested
  @DisplayName("InitializeAndSaveInjectExpectation")
  class InitializeAndSaveInjectExpectation {
    @Test
    @DisplayName("given one player in a team should create two inject expectations")
    void given_one_player_in_a_team_should_create_two_inject_expectations() {
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(team), List.of(), List.of(), List.of());

      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();

      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());
      assertThat(saved).hasSize(2);
      long teamExpectation =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() == null)
              .count();
      assertThat(teamExpectation).isEqualTo(1);
      long playerExpectation =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() != null)
              .count();
      assertThat(playerExpectation).isEqualTo(1);
    }

    @Test
    @DisplayName("given one disabled player in a team should not create expectation")
    void given_one_disabled_player_in_a_team_should_not_create_expectations() {
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();

      Exercise exercise =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultCrisisExercise())
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .withExercise(exerciseComposer.forExercise(exercise))
              .persist()
              .get();
      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(team), List.of(), List.of(), List.of());

      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();

      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());
      assertThat(saved).hasSize(0);
    }

    @Test
    @DisplayName("given two teams each attached to the same player should create 4 expectations")
    void given_two_teams_each_attached_to_the_same_player_should_create_4_expectations() {
      User player = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      Team team1 =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(player))
              .persist()
              .get();
      Team team2 =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(player))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team1))
              .withTeam(teamComposer.forTeam(team2))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(team1, team2), List.of(), List.of(), List.of());

      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();
    }

    @Test
    @DisplayName("given one team with one players should create result only on player lever")
    void given_one_team_with_one_player_should_create_result_on_player_level() {
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(team), List.of(), List.of(), List.of());

      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();

      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());
      assertThat(saved).hasSize(2);
      List<TableTopInjectExpectation> teamExpectations =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() == null)
              .toList();
      assertThat(teamExpectations.size()).isEqualTo(1);
      assertThat(teamExpectations.getFirst().getResults()).isEmpty();
      List<TableTopInjectExpectation> playerExpectations =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() != null)
              .toList();
      assertThat(playerExpectations.size()).isEqualTo(1);
      assertThat(playerExpectations.getFirst().getResults().size()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("GetLeaves")
  class GetLeaves {
    @Test
    @DisplayName("given player expectation should return this expectation")
    void given_player_expectation_should_return_this_expectation() {
      User player = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(player))
              .persist()
              .get();
      TableTopInjectExpectation playerExpectation = new ArticleInjectExpectation();
      playerExpectation.setTeam(team);
      playerExpectation.setUser(player);
      playerExpectation.setExpectedScore(100.0);

      List<? extends BaseInjectExpectation> leafExpectations =
          articleBehavior.getLeaves(playerExpectation);
      assertThat(leafExpectations).hasSize(1);
      assertThat(leafExpectations.getFirst()).isEqualTo(playerExpectation);
    }

    @Test
    @DisplayName("given team expectation should return the players expectations within this team")
    void given_team_expectation_should_return_the_players_expectations_within_this_team() {
      String articleExpectationName = "Article expectation name";
      User player1 = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      User player2 = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(player1))
              .withUser(userComposer.forUser(player2))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .withExpectation(
                  expectationComposer.forExpectation(
                      ExpectationFixture.createArticleInjectExpectationForPlayer(
                          team, player1, articleExpectationName)))
              .withExpectation(
                  expectationComposer.forExpectation(
                      ExpectationFixture.createArticleInjectExpectationForPlayer(
                          team, player2, articleExpectationName)))
              .persist()
              .get();

      TableTopInjectExpectation teamExpectation = new ArticleInjectExpectation();
      teamExpectation.setTeam(team);
      teamExpectation.setUser(null);
      teamExpectation.setExpectedScore(100.0);
      teamExpectation.setInject(inject);
      teamExpectation.setName(articleExpectationName);

      List<? extends BaseInjectExpectation> leafExpectations =
          articleBehavior.getLeaves(teamExpectation);
      assertThat(leafExpectations).hasSize(2);
    }
  }

  @Nested
  @DisplayName("UpdateInjectExpectationUsingBehaviors")
  class UpdateInjectExpectationUsingBehaviors {

    @Test
    @DisplayName(
        "given player expectation update should persist score on player and recompute team parent")
    void given_player_expectation_update_should_persist_score_on_player_and_recompute_team() {
      // Arrange
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();
      User player = team.getUsers().getFirst();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();

      ArticleInjectExpectation playerExpectation =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);
      playerExpectation.setUser(player);
      playerExpectation.setResults(
          new java.util.ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(MEDIA_PRESSURE_SOURCE_ID)
                      .result(null)
                      .score(null)
                      .build())));

      ArticleInjectExpectation teamExpectation =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);

      injectExpectationRepository.saveAll(List.of(playerExpectation, teamExpectation));
      entityManager.flush();
      entityManager.refresh(inject);

      ExpectationUpdateInput input =
          ExpectationFixture.getExpectationUpdateInput(MEDIA_PRESSURE_SOURCE_ID, 80.0);

      // Act
      injectExpectationService.updateInjectExpectationUsingBehaviors(
          playerExpectation.getId(), input);
      entityManager.flush();
      entityManager.clear();

      // Assert — re-fetch from DB to verify persistence
      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());

      // Player expectation should have the score and result persisted
      TableTopInjectExpectation savedPlayer =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() != null)
              .findFirst()
              .orElseThrow();
      assertThat(savedPlayer.getScore())
          .isEqualTo(80.0); // the score should be copied on player levels
      assertThat(savedPlayer.getResults()).hasSize(1);
      assertThat(savedPlayer.getResults().getFirst().getScore()).isEqualTo(80.0);

      // Team parent expectation should have its score recomputed and persisted
      TableTopInjectExpectation savedTeam =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() == null)
              .findFirst()
              .orElseThrow();
      // the parent score is 0 if expectation failed or equals to expectedScore if expectation
      // succeed
      // in this case the player score = 80, it's under the expectedScore (100) => so the team
      // expectation failed
      assertThat(savedTeam.getScore())
          .isEqualTo(0.0); // 80 is under expected score (100) so the parent score is 0
      assertThat(savedTeam.getResults()).hasSize(0);
    }

    @Test
    @DisplayName(
        "given team expectation update should persist score on all players and recompute team")
    void given_team_expectation_update_should_persist_score_on_players_and_team() {
      // Arrange
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();
      User player = team.getUsers().getFirst();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();

      ArticleInjectExpectation playerExpectation1 =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);
      playerExpectation1.setUser(player);
      playerExpectation1.setResults(
          new java.util.ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(MEDIA_PRESSURE_SOURCE_ID)
                      .result(null)
                      .score(null)
                      .build())));

      ArticleInjectExpectation playerExpectation2 =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);
      playerExpectation2.setUser(player);
      playerExpectation2.setResults(
          new java.util.ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(MEDIA_PRESSURE_SOURCE_ID)
                      .result(null)
                      .score(null)
                      .build())));

      ArticleInjectExpectation teamExpectation =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);

      injectExpectationRepository.saveAll(
          List.of(playerExpectation1, playerExpectation2, teamExpectation));
      entityManager.flush();
      entityManager.refresh(inject);

      ExpectationUpdateInput input =
          ExpectationFixture.getExpectationUpdateInput(MEDIA_PRESSURE_SOURCE_ID, 70.0);

      // Act
      injectExpectationService.updateInjectExpectationUsingBehaviors(
          teamExpectation.getId(), input);
      entityManager.flush();
      entityManager.clear();

      // Assert — re-fetch from DB to verify persistence
      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());

      // Player expectation should have the score propagated and persisted
      List<TableTopInjectExpectation> savedPlayers =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() != null)
              .toList();
      assertThat(savedPlayers).hasSize(2);
      assertTrue(savedPlayers.stream().allMatch(r -> r.getScore() == 70.0));
      //            assertTrue(savedPlayers.stream().allMatch(r -> r.get() == 70.0));
      assertTrue(savedPlayers.stream().allMatch(r -> r.getResults().getFirst().getScore() == 70.0));

      // Team expectation should have its score recomputed and persisted
      List<TableTopInjectExpectation> savedTeam =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() == null)
              .toList();
      assertThat(savedTeam).hasSize(1);
      assertTrue(savedTeam.stream().allMatch(r -> r.getScore() == 0.0));
    }

    @Nested
    @DisplayName("Context : all player need to validate the team expectation")
    class AllPlayerNeedToValidate {

      private static Stream<Arguments> allMustValidateScenarios() {
        return Stream.of(
            Arguments.of(100.0, null, 100.0, null, "two out of three succeed, team stays pending"),
            Arguments.of(100.0, null, 80.0, 0.0, "one player fails, team fails immediately"),
            Arguments.of(100.0, 100.0, 100.0, 100.0, "all three succeed, team succeeds"));
      }

      @ParameterizedTest(name = "{4}")
      @MethodSource("allMustValidateScenarios")
      void given_three_players_when_updating_third_should_compute_expected_team_score(
          Double p1PreScore,
          Double p2PreScore,
          Double updateScore,
          Double expectedTeamScore,
          String scenario) {
        assertTeamScoreAfterPlayerUpdate(
            false, p1PreScore, p2PreScore, updateScore, expectedTeamScore);
      }
    }

    @Nested
    @DisplayName("Context : at least one player need to validate the team expectation")
    class AtLeastOnePlayerNeedToValidate {

      private static Stream<Arguments> atLeastOneMustValidateScenarios() {
        return Stream.of(
            Arguments.of(
                null, null, 100.0, 100.0, "first player succeeds, team succeeds immediately"),
            Arguments.of(null, null, 80.0, null, "first player fails, team stays pending"),
            Arguments.of(
                80.0, 80.0, 100.0, 100.0, "first two failed, third succeeds, team succeeds"));
      }

      @ParameterizedTest(name = "{4}")
      @MethodSource("atLeastOneMustValidateScenarios")
      void given_three_players_when_updating_should_compute_expected_team_score(
          Double p1PreScore,
          Double p2PreScore,
          Double updateScore,
          Double expectedTeamScore,
          String scenario) {
        assertTeamScoreAfterPlayerUpdate(
            true, p1PreScore, p2PreScore, updateScore, expectedTeamScore);
      }
    }

    // -- Shared helpers for parameterized team-score tests --

    private void assertTeamScoreAfterPlayerUpdate(
        boolean isGroup,
        Double p1PreScore,
        Double p2PreScore,
        Double updateScore,
        Double expectedTeamScore) {
      // Arrange — 3 players in one team
      User player1 = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      User player2 = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      User player3 = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();

      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(player1))
              .withUser(userComposer.forUser(player2))
              .withUser(userComposer.forUser(player3))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();

      ArticleInjectExpectation teamExpectation =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);
      teamExpectation.setExpectationGroup(isGroup);

      ArticleInjectExpectation p1Expectation =
          createPlayerExpectation(team, player1, inject, isGroup, p1PreScore);
      ArticleInjectExpectation p2Expectation =
          createPlayerExpectation(team, player2, inject, isGroup, p2PreScore);
      ArticleInjectExpectation p3Expectation =
          createPlayerExpectation(team, player3, inject, isGroup, null);

      injectExpectationRepository.saveAll(
          List.of(teamExpectation, p1Expectation, p2Expectation, p3Expectation));
      entityManager.flush();
      entityManager.refresh(inject);

      ExpectationUpdateInput input =
          ExpectationFixture.getExpectationUpdateInput(MEDIA_PRESSURE_SOURCE_ID, updateScore);
      input.setSourceName("Player Manual Validation");
      input.setSourceType("player-manual-validation");

      // Act — update player3
      injectExpectationService.updateInjectExpectationUsingBehaviors(p3Expectation.getId(), input);
      entityManager.flush();
      entityManager.clear();

      // Assert — re-fetch team expectation from DB
      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());

      TableTopInjectExpectation savedTeam =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() == null)
              .findFirst()
              .orElseThrow();
      assertThat(savedTeam.getScore()).isEqualTo(expectedTeamScore);
    }

    private ArticleInjectExpectation createPlayerExpectation(
        Team team, User player, Inject inject, boolean isGroup, Double preScore) {
      ArticleInjectExpectation expectation =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);
      expectation.setUser(player);
      expectation.setExpectationGroup(isGroup);
      expectation.setResults(
          new java.util.ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(MEDIA_PRESSURE_SOURCE_ID)
                      .result(null)
                      .score(null)
                      .build())));
      if (preScore != null) {
        expectation.setScore(preScore);
        expectation.getResults().getFirst().setScore(preScore);
        expectation
            .getResults()
            .getFirst()
            .setResult(
                preScore >= expectation.getExpectedScore()
                    ? expectation.getSuccessLabel()
                    : expectation.getFailureLabel());
      }
      return expectation;
    }
  }
}
