package io.openaev.service.expectation;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static io.openaev.injectors.phishing.service.PhishingTrackingService.NO_INTERACTION_MESSAGE;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.MEDIA_PRESSURE_SOURCE_ID;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildDefaultForPlayerManualValidation;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForPlayerManualValidation;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForTeamManualValidation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerJob;
import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.rest.exercise.form.InjectExpectationUpdateFromUIInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

  // Source sent by the manual validation form of the UI (ManualExpectationsValidationForm.tsx)
  private static final String UI_SOURCE_ID = "ui";

  @Autowired private ArticleBehavior articleBehavior;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private ExpectationsExpirationManagerService expectationsExpirationManagerService;
  @Autowired private ExpectationsExpirationManagerJob expectationsExpirationManagerJob;
  @Autowired private ExpectationsExpirationManagerConfig expectationsExpirationManagerConfig;

  @Autowired private ArticleComposer articleComposer;
  @Autowired private ChannelComposer channelComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectExpectationComposer expectationComposer;

  @BeforeEach
  void setUp() {
    articleComposer.reset();
    channelComposer.reset();
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
              false, false, inject, List.of(team), List.of(), List.of(), List.of(), List.of());
      Article article =
          articleComposer
              .forArticle(ArticleFixture.getDefaultArticle())
              .withChannel(channelComposer.forChannel(ChannelFixture.getDefaultChannel()))
              .persist()
              .get();
      executableInject.cacheExpectationContext(List.of(article));

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
              false, false, inject, List.of(team), List.of(), List.of(), List.of(), List.of());

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
              false,
              false,
              inject,
              List.of(team1, team2),
              List.of(),
              List.of(),
              List.of(),
              List.of());

      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();
    }

    @Test
    @DisplayName("given one team with one player should create result only on player lever")
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
              false, false, inject, List.of(team), List.of(), List.of(), List.of(), List.of());
      Article article =
          articleComposer
              .forArticle(ArticleFixture.getDefaultArticle())
              .withChannel(channelComposer.forChannel(ChannelFixture.getDefaultChannel()))
              .persist()
              .get();
      executableInject.cacheExpectationContext(List.of(article));

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

    @Test
    @DisplayName(
        "given one team with one player and two articles should create 4 expectations, 2 for each article")
    void given_one_team_with_one_player_and_two_articles_should_create_4_expectations() {
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
              false, false, inject, List.of(team), List.of(), List.of(), List.of(), List.of());
      Article article =
          articleComposer
              .forArticle(ArticleFixture.getDefaultArticle())
              .withChannel(channelComposer.forChannel(ChannelFixture.getDefaultChannel()))
              .persist()
              .get();
      Article article2 =
          articleComposer
              .forArticle(ArticleFixture.getDefaultArticle())
              .withChannel(channelComposer.forChannel(ChannelFixture.getDefaultChannel()))
              .persist()
              .get();
      executableInject.cacheExpectationContext(List.of(article, article2));

      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();

      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());
      assertThat(saved).hasSize(4);
      // Should have two expectation for each article, one for team and one for player
      long article1TeamExpectation =
          saved.stream()
              .map(ArticleInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() == null
                          && expectation.getArticle().getId().equals(article.getId()))
              .count();
      assertThat(article1TeamExpectation).isEqualTo(1);
      long article1PlayerExpectation =
          saved.stream()
              .map(ArticleInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() != null
                          && expectation.getArticle().getId().equals(article.getId()))
              .count();
      assertThat(article1PlayerExpectation).isEqualTo(1);
      long article2TeamExpectation =
          saved.stream()
              .map(ArticleInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() == null
                          && expectation.getArticle().getId().equals(article2.getId()))
              .count();
      assertThat(article2TeamExpectation).isEqualTo(1);
      long article2PlayerExpectation =
          saved.stream()
              .map(ArticleInjectExpectation.class::cast)
              .filter(
                  expectation ->
                      expectation.getTeam().getId().equals(team.getId())
                          && expectation.getUser() != null
                          && expectation.getArticle().getId().equals(article2.getId()))
              .count();
      assertThat(article2PlayerExpectation).isEqualTo(1);
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
                      .sourceType("unit-test")
                      .sourcePlatform("test-harness")
                      .sourceName("Unit tests")
                      .date(Instant.now())
                      .result(null)
                      .score(null)
                      .build())));

      ArticleInjectExpectation teamExpectation =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);

      injectExpectationRepository.saveAll(List.of(playerExpectation, teamExpectation));
      entityManager.flush();
      entityManager.refresh(inject);

      ExpectationVerdict input =
          ExpectationFixture.getExpectationVerdict(MEDIA_PRESSURE_SOURCE_ID, 80.0);

      // Act
      injectExpectationService.updateInjectExpectation(playerExpectation.getId(), input);
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
                      .sourceType("unit-test")
                      .sourcePlatform("test-harness")
                      .sourceName("Unit tests")
                      .date(Instant.now())
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
                      .sourceType("unit-test")
                      .sourcePlatform("test-harness")
                      .sourceName("Unit tests")
                      .date(Instant.now())
                      .result(null)
                      .score(null)
                      .build())));

      ArticleInjectExpectation teamExpectation =
          InjectExpectationFixture.createArticleInjectExpectation(team, inject);

      injectExpectationRepository.saveAll(
          List.of(playerExpectation1, playerExpectation2, teamExpectation));
      entityManager.flush();
      entityManager.refresh(inject);

      ExpectationVerdict input =
          ExpectationFixture.getExpectationVerdict(MEDIA_PRESSURE_SOURCE_ID, 70.0);

      // Act
      injectExpectationService.updateInjectExpectation(teamExpectation.getId(), input);
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

      InjectExpectationUpdateFromUIInput uiInput =
          ExpectationFixture.getExpectationUpdateInput(MEDIA_PRESSURE_SOURCE_ID, updateScore);
      uiInput.setSourceName("Player Manual Validation");
      uiInput.setSourceType("player-manual-validation");
      ExpectationVerdict input = ExpectationVerdict.fromUI(uiInput);

      // Act — update player3
      injectExpectationService.updateInjectExpectation(p3Expectation.getId(), input);
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
                      .sourceType("unit-test")
                      .sourcePlatform("test-harness")
                      .sourceName("Unit tests")
                      .date(Instant.now())
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

  @Nested
  @DisplayName("Human response replaces previous results")
  class HumanResponseReplacesPreviousResults {

    @Test
    @DisplayName(
        "given pending player expectation when validated from UI should score player and team")
    void given_pending_player_expectation_when_validated_from_ui_should_score_player_and_team() {
      // Arrange
      Team team = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation playerExpectation =
          createPlayerExpectation(
              team, team.getUsers().getFirst(), inject, buildDefaultForPlayerManualValidation());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(playerExpectation, teamExpectation));

      // Act
      injectExpectationService.updateInjectExpectation(
          playerExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));
      entityManager.flush();
      entityManager.clear();

      // Assert
      List<TableTopInjectExpectation> savedPlayers = findPlayerExpectations(inject);
      assertThat(savedPlayers).hasSize(1);
      assertOnlyResultFromUi(savedPlayers.getFirst(), 100.0);
      assertThat(findTeamExpectation(inject).getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("given pending players when team validated from UI should score players and team")
    void given_pending_players_when_team_validated_from_ui_should_score_players_and_team() {
      // Arrange
      Team team = persistTeamWithPlayers(2);
      Inject inject = persistInjectForTeam(team);
      List<ManualInjectExpectation> expectations =
          new ArrayList<>(
              team.getUsers().stream()
                  .map(
                      player ->
                          createPlayerExpectation(
                              team, player, inject, buildDefaultForPlayerManualValidation()))
                  .toList());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      expectations.add(teamExpectation);
      persistExpectations(inject, expectations);

      // Act
      injectExpectationService.updateInjectExpectation(
          teamExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));
      entityManager.flush();
      entityManager.clear();

      // Assert
      List<TableTopInjectExpectation> savedPlayers = findPlayerExpectations(inject);
      assertThat(savedPlayers).hasSize(2);
      savedPlayers.forEach(player -> assertOnlyResultFromUi(player, 100.0));
      assertThat(findTeamExpectation(inject).getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName(
        "given player with successful default result when failed from UI should fail player")
    void given_player_with_successful_default_result_when_failed_from_ui_should_fail_player() {
      // Arrange — same default result as a phishing player expectation ("resisted" at send time)
      Team team = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation playerExpectation =
          createPlayerExpectation(
              team,
              team.getUsers().getFirst(),
              inject,
              buildForPlayerManualValidation(NO_INTERACTION_MESSAGE, 100.0));
      playerExpectation.setScore(100.0);
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(playerExpectation, teamExpectation));

      // Act
      injectExpectationService.updateInjectExpectation(
          playerExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 0.0));
      entityManager.flush();
      entityManager.clear();

      // Assert
      List<TableTopInjectExpectation> savedPlayers = findPlayerExpectations(inject);
      assertThat(savedPlayers).hasSize(1);
      assertOnlyResultFromUi(savedPlayers.getFirst(), 0.0);
      assertThat(findTeamExpectation(inject).getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("given team without players when validated from UI should score the team")
    void given_team_without_players_when_validated_from_ui_should_score_the_team() {
      // Arrange — an atomic testing creates the team expectation even for an empty team
      Team team = persistTeamWithPlayers(0);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(teamExpectation));

      // Act
      injectExpectationService.updateInjectExpectation(
          teamExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(findPlayerExpectations(inject)).isEmpty();
      assertOnlyResultFromUi(findTeamExpectation(inject), 100.0);
    }
  }

  @Nested
  @DisplayName("Delete result from UI")
  class DeleteResultFromUi {

    @Test
    @DisplayName("given player validated from UI when result deleted should reset player and team")
    void given_player_validated_from_ui_when_result_deleted_should_reset_player_and_team() {
      // Arrange
      Team team = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation playerExpectation =
          createPlayerExpectation(
              team, team.getUsers().getFirst(), inject, buildDefaultForPlayerManualValidation());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(playerExpectation, teamExpectation));
      injectExpectationService.updateInjectExpectation(
          playerExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));

      // Act
      injectExpectationService.deleteInjectExpectationResult(
          playerExpectation.getId(), UI_SOURCE_ID);
      entityManager.flush();
      entityManager.clear();

      // Assert
      List<TableTopInjectExpectation> savedPlayers = findPlayerExpectations(inject);
      assertThat(savedPlayers).hasSize(1);
      assertNoResult(savedPlayers.getFirst());
      assertThat(findTeamExpectation(inject).getScore()).isNull();
    }

    @Test
    @DisplayName("given team validated from UI when result deleted should reset players and team")
    void given_team_validated_from_ui_when_result_deleted_should_reset_players_and_team() {
      // Arrange
      Team team = persistTeamWithPlayers(2);
      Inject inject = persistInjectForTeam(team);
      List<ManualInjectExpectation> expectations =
          new ArrayList<>(
              team.getUsers().stream()
                  .map(
                      player ->
                          createPlayerExpectation(
                              team, player, inject, buildDefaultForPlayerManualValidation()))
                  .toList());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      expectations.add(teamExpectation);
      persistExpectations(inject, expectations);
      injectExpectationService.updateInjectExpectation(
          teamExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));

      // Act
      injectExpectationService.deleteInjectExpectationResult(teamExpectation.getId(), UI_SOURCE_ID);
      entityManager.flush();
      entityManager.clear();

      // Assert
      List<TableTopInjectExpectation> savedPlayers = findPlayerExpectations(inject);
      assertThat(savedPlayers).hasSize(2).allSatisfy(player -> assertNoResult(player));
      assertThat(findTeamExpectation(inject).getScore()).isNull();
    }

    @Test
    @DisplayName(
        "given team validated before behavior update when result deleted should reset players and team")
    void
        given_team_validated_before_behavior_update_when_result_deleted_should_reset_players_and_team() {
      // Arrange
      Team team = persistTeamWithPlayers(2);
      Inject inject = persistInjectForTeam(team);
      List<ManualInjectExpectation> expectations =
          new ArrayList<>(
              team.getUsers().stream()
                  .map(
                      player -> {
                        ManualInjectExpectation playerExpectation =
                            createPlayerExpectation(
                                team,
                                player,
                                inject,
                                buildForTeamManualValidation("Success", 100.0));
                        playerExpectation.setScore(100.0);
                        return playerExpectation;
                      })
                  .toList());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      teamExpectation.setResults(
          new ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(UI_SOURCE_ID)
                      .sourceType("unit-test")
                      .sourcePlatform("test-harness")
                      .sourceName("Unit tests")
                      .date(Instant.now())
                      .result("Success")
                      .score(100.0)
                      .build())));
      teamExpectation.setScore(100.0);
      expectations.add(teamExpectation);
      persistExpectations(inject, expectations);

      // Act
      injectExpectationService.deleteInjectExpectationResult(teamExpectation.getId(), UI_SOURCE_ID);
      entityManager.flush();
      entityManager.clear();

      // Assert
      List<TableTopInjectExpectation> savedPlayers = findPlayerExpectations(inject);
      assertThat(savedPlayers).hasSize(2).allSatisfy(player -> assertNoResult(player));
      assertNoResult(findTeamExpectation(inject));
    }

    @Test
    @DisplayName("given team without players when result deleted should reset the team")
    void given_team_without_players_when_result_deleted_should_reset_the_team() {
      // Arrange
      Team team = persistTeamWithPlayers(0);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(teamExpectation));
      injectExpectationService.updateInjectExpectation(
          teamExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));

      // Act
      injectExpectationService.deleteInjectExpectationResult(teamExpectation.getId(), UI_SOURCE_ID);
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(findPlayerExpectations(inject)).isEmpty();
      assertNoResult(findTeamExpectation(inject));
    }

    @Test
    @DisplayName("given another source when result deleted should still reset player and team")
    void given_another_source_when_result_deleted_should_still_reset_player_and_team() {
      // Arrange
      Team team = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation playerExpectation =
          createPlayerExpectation(
              team, team.getUsers().getFirst(), inject, buildDefaultForPlayerManualValidation());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(playerExpectation, teamExpectation));
      injectExpectationService.updateInjectExpectation(
          playerExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));

      // Act
      injectExpectationService.deleteInjectExpectationResult(
          playerExpectation.getId(), MEDIA_PRESSURE_SOURCE_ID);
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertNoResult(findPlayerExpectations(inject).getFirst());
      assertThat(findTeamExpectation(inject).getScore()).isNull();
    }

    private void assertNoResult(TableTopInjectExpectation expectation) {
      assertThat(expectation.getResults()).isEmpty();
      assertThat(expectation.getScore()).isNull();
    }
  }

  @Nested
  @DisplayName("Inject with several articles")
  class InjectWithSeveralArticles {

    private Inject inject;
    private Team team;
    private Article article1;
    private Article article2;

    @BeforeEach
    void arrangeOneTeamWithTwoPlayersAndTwoArticles() {
      team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();
      inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();
      article1 = persistArticle();
      article2 = persistArticle();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(team), List.of(), List.of(), List.of(), List.of());
      executableInject.cacheExpectationContext(List.of(article1, article2));
      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);
      // At least one player must read: a single read is enough to settle the team
      template.setExpectationGroup(true);

      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null);
      entityManager.flush();
      entityManager.refresh(inject);
    }

    @Test
    @DisplayName("given player reads first article should not recompute team of second article")
    void given_player_reads_first_article_should_not_recompute_team_of_second_article() {
      // Arrange
      ArticleInjectExpectation reader = findPlayerExpectations(article1).getFirst();

      // Act
      injectExpectationService.updateInjectExpectation(
          reader.getId(),
          ExpectationFixture.getExpectationVerdict(MEDIA_PRESSURE_SOURCE_ID, 100.0));
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(findTeamExpectation(article1).getScore()).isEqualTo(100.0);
      assertThat(findTeamExpectation(article2).getScore()).isNull();
      assertThat(findPlayerExpectations(article2))
          .hasSize(2)
          .allSatisfy(player -> assertThat(player.getScore()).isNull());
    }

    @Test
    @DisplayName("given team validated on first article should only update its players")
    void given_team_validated_on_first_article_should_only_update_its_players() {
      // Arrange
      ArticleInjectExpectation teamExpectation = findTeamExpectation(article1);

      // Act
      injectExpectationService.updateInjectExpectation(
          teamExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(findPlayerExpectations(article1))
          .hasSize(2)
          .allSatisfy(player -> assertThat(player.getScore()).isEqualTo(100.0));
      assertThat(findPlayerExpectations(article2))
          .hasSize(2)
          .allSatisfy(player -> assertThat(player.getScore()).isNull());
      assertThat(findTeamExpectation(article2).getScore()).isNull();
    }

    @Test
    @DisplayName("given players of first article expired should only derive team of first article")
    void given_expiredPlayersOfFirstArticle_should_onlyDeriveTeamOfFirstArticle() throws Exception {
      // Arrange: only the players of the first article are expired
      entityManager
          .createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_created_at = :past WHERE inject_expectation_id IN (:ids)")
          .setParameter("past", Instant.now().minus(1, ChronoUnit.DAYS))
          .setParameter(
              "ids",
              findPlayerExpectations(article1).stream().map(BaseInjectExpectation::getId).toList())
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();
      String tenantId = inject.getTenant().getId();
      expectationsExpirationManagerJob.registerForTenant(tenantId);

      // Act
      expectationsExpirationManagerService.computeExpectations(tenantId);
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(findPlayerExpectations(article1))
          .hasSize(2)
          .allSatisfy(player -> assertThat(player.getScore()).isEqualTo(0.0));
      assertThat(findTeamExpectation(article1).getScore()).isEqualTo(0.0);
      assertThat(findPlayerExpectations(article2))
          .hasSize(2)
          .allSatisfy(player -> assertThat(player.getScore()).isNull());
      assertThat(findTeamExpectation(article2).getScore()).isNull();
    }

    @Test
    @DisplayName("given several players of the same team should recompute their team once")
    void given_severalPlayersOfSameTeam_should_recomputeTheirTeamOnce() {
      // Arrange
      List<ArticleInjectExpectation> players = findPlayerExpectations(article1);
      players.forEach(player -> player.setScore(100.0));

      // Act
      List<? extends BaseInjectExpectation> recomputed =
          articleBehavior.recomputeParentScores(players);

      // Assert: only the team of the first article, once
      assertThat(recomputed)
          .singleElement()
          .satisfies(
              team -> {
                assertThat(team.getId()).isEqualTo(findTeamExpectation(article1).getId());
                assertThat(team.getScore()).isEqualTo(100.0);
              });
    }

    private Article persistArticle() {
      return articleComposer
          .forArticle(ArticleFixture.getDefaultArticle())
          .withChannel(channelComposer.forChannel(ChannelFixture.getDefaultChannel()))
          .persist()
          .get();
    }

    private List<ArticleInjectExpectation> findArticleExpectations(Article article) {
      return injectExpectationRepository.findAllByInjectId(inject.getId()).stream()
          .map(ArticleInjectExpectation.class::cast)
          .filter(expectation -> expectation.getArticle().getId().equals(article.getId()))
          .toList();
    }

    private List<ArticleInjectExpectation> findPlayerExpectations(Article article) {
      return findArticleExpectations(article).stream().filter(e -> e.getUser() != null).toList();
    }

    private ArticleInjectExpectation findTeamExpectation(Article article) {
      return findArticleExpectations(article).stream()
          .filter(e -> e.getUser() == null)
          .findFirst()
          .orElseThrow();
    }
  }

  @Nested
  @DisplayName("Expiration")
  class Expiration {

    @Test
    @DisplayName("given pending players when expired should fail the players and derive the team")
    void given_expiredPendingPlayers_should_failPlayersAndDeriveTeam() throws Exception {
      // Arrange
      Team team = persistTeamWithPlayers(2);
      Inject inject = persistInjectForTeam(team);
      List<ManualInjectExpectation> expectations =
          new ArrayList<>(
              team.getUsers().stream()
                  .map(
                      player ->
                          createPlayerExpectation(
                              team, player, inject, buildDefaultForPlayerManualValidation()))
                  .toList());
      expectations.add(InjectExpectationFixture.createManualInjectExpectation(team, inject));
      persistExpectations(inject, expectations);
      backdateExpectations(inject);

      // Act
      expireExpectations(inject);

      // Assert
      assertThat(findPlayerExpectations(inject))
          .hasSize(2)
          .allSatisfy(player -> assertOnlyExpirationResult(player, 0.0));
      TableTopInjectExpectation savedTeam = findTeamExpectation(inject);
      assertThat(savedTeam.getScore()).isEqualTo(0.0);
      assertThat(savedTeam.getResults())
          .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId()));
    }

    @Test
    @DisplayName(
        "given players answered from UI should score the team before expiration and keep it after")
    void given_playersAnsweredFromUi_should_scoreTeamBeforeExpirationAndKeepItAfter()
        throws Exception {
      // Arrange: the players answer through the behaviors, which recompute their team at once. A
      // team is thus never left pending with all its players scored, and the expiration, which
      // selects the pending leaves only, has nothing left to derive.
      Team team = persistTeamWithPlayers(2);
      Inject inject = persistInjectForTeam(team);
      List<ManualInjectExpectation> expectations =
          new ArrayList<>(
              team.getUsers().stream()
                  .map(
                      player ->
                          createPlayerExpectation(
                              team, player, inject, buildDefaultForPlayerManualValidation()))
                  .toList());
      expectations.add(InjectExpectationFixture.createManualInjectExpectation(team, inject));
      persistExpectations(inject, expectations);
      expectations.stream()
          .filter(expectation -> expectation.getUser() != null)
          .forEach(
              player ->
                  injectExpectationService.updateInjectExpectation(
                      player.getId(),
                      ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0)));
      entityManager.flush();
      entityManager.clear();
      assertThat(findTeamExpectation(inject).getScore()).isEqualTo(100.0);
      backdateExpectations(inject);

      // Act
      expireExpectations(inject);

      // Assert: the answers are kept, the expiration did not touch the players nor the team
      assertThat(findPlayerExpectations(inject))
          .hasSize(2)
          .allSatisfy(player -> assertOnlyResultFromUi(player, 100.0));
      TableTopInjectExpectation savedTeam = findTeamExpectation(inject);
      assertThat(savedTeam.getScore()).isEqualTo(100.0);
      assertThat(savedTeam.getResults())
          .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId()));
    }

    @Test
    @DisplayName("given team without players when expired should fail the team directly")
    void given_expiredTeamWithoutPlayers_should_failTeamDirectly() throws Exception {
      // Arrange
      Team team = persistTeamWithPlayers(0);
      Inject inject = persistInjectForTeam(team);
      persistExpectations(
          inject, List.of(InjectExpectationFixture.createManualInjectExpectation(team, inject)));
      backdateExpectations(inject);

      // Act
      expireExpectations(inject);

      // Assert
      assertThat(findPlayerExpectations(inject)).isEmpty();
      assertOnlyExpirationResult(findTeamExpectation(inject), 0.0);
    }

    @Test
    @DisplayName("given expectation without team should fail it directly")
    void given_expiredExpectationWithoutTeam_should_failItDirectly() throws Exception {
      // Arrange: a legacy MANUAL expectation created by a technical injector on an asset has no
      // team nor player, and shares its inject with a team expectation and its player
      Team team = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation teamlessExpectation =
          InjectExpectationFixture.createManualInjectExpectation(null, inject);
      persistExpectations(
          inject,
          List.of(
              createPlayerExpectation(
                  team,
                  team.getUsers().getFirst(),
                  inject,
                  buildDefaultForPlayerManualValidation()),
              InjectExpectationFixture.createManualInjectExpectation(team, inject),
              teamlessExpectation));
      backdateExpectations(inject);

      // Act
      expireExpectations(inject);

      // Assert: the teamless expectation is its own leaf, the team still derives from its player
      assertOnlyExpirationResult(
          (TableTopInjectExpectation)
              injectExpectationRepository.findById(teamlessExpectation.getId()).orElseThrow(),
          0.0);
      assertThat(findPlayerExpectations(inject))
          .singleElement()
          .satisfies(player -> assertOnlyExpirationResult(player, 0.0));
      TableTopInjectExpectation savedTeam = findTeamExpectation(inject);
      assertThat(savedTeam.getScore()).isEqualTo(0.0);
      assertThat(savedTeam.getResults())
          .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId()));
    }

    @Test
    @DisplayName(
        "given team older than its players beyond the batch limit should still expire the players")
    void given_teamOlderThanPlayersBeyondBatchLimit_should_expirePlayersAndDeriveTeam()
        throws Exception {
      // Arrange: a team is saved before its players, so it is older. With a batch of one, a batch
      // holding parents would select the pending team on every run and never reach the players
      Team team = persistTeamWithPlayers(2);
      Inject inject = persistInjectForTeam(team);
      List<ManualInjectExpectation> expectations =
          new ArrayList<>(
              team.getUsers().stream()
                  .map(
                      player ->
                          createPlayerExpectation(
                              team, player, inject, buildDefaultForPlayerManualValidation()))
                  .toList());
      expectations.add(InjectExpectationFixture.createManualInjectExpectation(team, inject));
      persistExpectations(inject, expectations);
      backdateTeamBeforePlayers(inject);
      int batchSize = expectationsExpirationManagerConfig.getBatchSize();
      expectationsExpirationManagerConfig.setBatchSize(1);

      // Act: one player per run
      try {
        expireExpectations(inject);
        expireExpectations(inject);
      } finally {
        expectationsExpirationManagerConfig.setBatchSize(batchSize);
      }

      // Assert
      assertThat(findPlayerExpectations(inject))
          .hasSize(2)
          .allSatisfy(player -> assertOnlyExpirationResult(player, 0.0));
      assertThat(findTeamExpectation(inject).getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("given players of two teams expired in one run should derive both teams")
    void given_expiredPlayersOfTwoTeams_should_deriveBothTeams() throws Exception {
      // Arrange
      Team firstTeam = persistTeamWithPlayers(2);
      Team secondTeam = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(firstTeam);
      List<ManualInjectExpectation> expectations = new ArrayList<>();
      for (Team team : List.of(firstTeam, secondTeam)) {
        team.getUsers()
            .forEach(
                player ->
                    expectations.add(
                        createPlayerExpectation(
                            team, player, inject, buildDefaultForPlayerManualValidation())));
        expectations.add(InjectExpectationFixture.createManualInjectExpectation(team, inject));
      }
      persistExpectations(inject, expectations);
      backdateExpectations(inject);

      // Act
      expireExpectations(inject);

      // Assert: each team is derived from its own players
      assertThat(findPlayerExpectations(inject))
          .hasSize(3)
          .allSatisfy(player -> assertThat(player.getScore()).isEqualTo(0.0));
      assertThat(injectExpectationRepository.findAllByInjectId(inject.getId()))
          .map(TableTopInjectExpectation.class::cast)
          .filteredOn(expectation -> expectation.getUser() == null)
          .hasSize(2)
          .allSatisfy(team -> assertThat(team.getScore()).isEqualTo(0.0));
    }

    /** Moves the team creation date further back than its players, as when it is saved first. */
    private void backdateTeamBeforePlayers(Inject inject) {
      backdateExpectations(inject);
      entityManager
          .createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_created_at = :past WHERE inject_id = :injectId AND user_id IS NULL")
          .setParameter("past", Instant.now().minus(2, ChronoUnit.DAYS))
          .setParameter("injectId", inject.getId())
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();
    }

    /** Moves the creation date back so the expiration query picks the expectations up. */
    private void backdateExpectations(Inject inject) {
      entityManager.flush();
      entityManager
          .createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_created_at = :past WHERE inject_id = :injectId")
          .setParameter("past", Instant.now().minus(1, ChronoUnit.DAYS))
          .setParameter("injectId", inject.getId())
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();
    }

    private void expireExpectations(Inject inject) throws Exception {
      String tenantId = inject.getTenant().getId();
      expectationsExpirationManagerJob.registerForTenant(tenantId);
      expectationsExpirationManagerService.computeExpectations(tenantId);
      entityManager.flush();
      entityManager.clear();
    }

    private void assertOnlyExpirationResult(
        TableTopInjectExpectation expectation, Double expectedScore) {
      assertThat(expectation.getScore()).isEqualTo(expectedScore);
      assertThat(expectation.getResults())
          .singleElement()
          .satisfies(
              result -> {
                assertThat(result.getSourceId()).isEqualTo(COLLECTOR_ID);
                assertThat(result.getScore()).isEqualTo(expectedScore);
              });
    }
  }

  @Nested
  @DisplayName("Manual expectations not filled")
  class ManualExpectationsNotFilled {

    @Test
    @DisplayName("given team and players without score should list them as not filled")
    void given_teamAndPlayersWithoutScore_should_listThemAsNotFilled() {
      // Arrange
      Team team = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation playerExpectation =
          createPlayerExpectation(
              team, team.getUsers().getFirst(), inject, buildDefaultForPlayerManualValidation());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(playerExpectation, teamExpectation));
      String tenantId = inject.getTenant().getId();

      // Act
      List<String> notFilledIds = ids(injectExpectationService.manualExpectationsNotFill(tenantId));
      List<String> notFilledForSourceIds =
          ids(injectExpectationService.manualExpectationsNotFill(tenantId, UI_SOURCE_ID));

      // Assert
      assertThat(notFilledIds).contains(playerExpectation.getId(), teamExpectation.getId());
      assertThat(notFilledForSourceIds)
          .contains(playerExpectation.getId(), teamExpectation.getId());
    }

    @Test
    @DisplayName("given team scored from its players should not list it as not filled")
    void given_teamScoredFromItsPlayers_should_notListItAsNotFilled() {
      // Arrange
      Team team = persistTeamWithPlayers(1);
      Inject inject = persistInjectForTeam(team);
      ManualInjectExpectation playerExpectation =
          createPlayerExpectation(
              team, team.getUsers().getFirst(), inject, buildDefaultForPlayerManualValidation());
      ManualInjectExpectation teamExpectation =
          InjectExpectationFixture.createManualInjectExpectation(team, inject);
      persistExpectations(inject, List.of(playerExpectation, teamExpectation));
      injectExpectationService.updateInjectExpectation(
          playerExpectation.getId(), ExpectationFixture.getExpectationVerdict(UI_SOURCE_ID, 100.0));
      entityManager.flush();
      entityManager.clear();
      String tenantId = inject.getTenant().getId();

      // Act
      List<String> notFilledIds = ids(injectExpectationService.manualExpectationsNotFill(tenantId));
      List<String> notFilledForSourceIds =
          ids(injectExpectationService.manualExpectationsNotFill(tenantId, COLLECTOR_ID));

      // Assert: the team has a score but no result of its own, it is no longer pending
      assertThat(findTeamExpectation(inject).getResults()).isEmpty();
      assertThat(notFilledIds).doesNotContain(playerExpectation.getId(), teamExpectation.getId());
      assertThat(notFilledForSourceIds)
          .doesNotContain(playerExpectation.getId(), teamExpectation.getId());
    }

    private List<String> ids(List<BaseInjectExpectation> expectations) {
      return expectations.stream().map(BaseInjectExpectation::getId).toList();
    }
  }

  // -- Shared helpers for human response tests --

  private Team persistTeamWithPlayers(int playerCount) {
    TeamComposer.Composer teamWrapper =
        teamComposer.forTeam(TeamFixture.getDefaultContextualTeam());
    for (int i = 0; i < playerCount; i++) {
      teamWrapper.withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()));
    }
    return teamWrapper.persist().get();
  }

  private Inject persistInjectForTeam(Team team) {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(
            injectorContractComposer.forInjectorContract(
                InjectorContractFixture.createDefaultInjectorContract()))
        .withTeam(teamComposer.forTeam(team))
        .persist()
        .get();
  }

  private ManualInjectExpectation createPlayerExpectation(
      Team team, User player, Inject inject, InjectExpectationResult defaultResult) {
    ManualInjectExpectation expectation =
        InjectExpectationFixture.createManualInjectExpectation(team, inject);
    expectation.setUser(player);
    expectation.setResults(new ArrayList<>(List.of(defaultResult)));
    return expectation;
  }

  private void persistExpectations(
      Inject inject, List<? extends BaseInjectExpectation> expectations) {
    injectExpectationRepository.saveAll(expectations);
    entityManager.flush();
    entityManager.refresh(inject);
  }

  private List<TableTopInjectExpectation> findPlayerExpectations(Inject inject) {
    return injectExpectationRepository.findAllByInjectId(inject.getId()).stream()
        .map(TableTopInjectExpectation.class::cast)
        .filter(e -> e.getUser() != null)
        .toList();
  }

  private TableTopInjectExpectation findTeamExpectation(Inject inject) {
    return injectExpectationRepository.findAllByInjectId(inject.getId()).stream()
        .map(TableTopInjectExpectation.class::cast)
        .filter(e -> e.getUser() == null && e.getTeam() != null)
        .findFirst()
        .orElseThrow();
  }

  private void assertOnlyResultFromUi(TableTopInjectExpectation player, Double expectedScore) {
    assertThat(player.getScore()).isEqualTo(expectedScore);
    assertThat(player.getResults()).hasSize(1);
    assertThat(player.getResults().getFirst().getSourceId()).isEqualTo(UI_SOURCE_ID);
    assertThat(player.getResults().getFirst().getScore()).isEqualTo(expectedScore);
  }
}
