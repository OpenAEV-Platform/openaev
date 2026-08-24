package io.openaev.service.expectation;

import static io.openaev.utils.challenge.ChallengeExpectationUtils.CHALLENGE_SOURCE_NAME;
import static io.openaev.utils.challenge.ChallengeExpectationUtils.CHALLENGE_SOURCE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("ChallengeBehavior")
class ChallengeBehaviorTest extends IntegrationTest {

  @Autowired private ChallengeBehavior challengeBehavior;
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;

  @BeforeEach
  void setUp() {
    teamComposer.reset();
    userComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
  }

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("given challenge expectation should return true")
    void given_challenge_expectation_should_return_true() {
      // Arrange
      ChallengeInjectExpectation expectation = new ChallengeInjectExpectation();

      // Act
      boolean supported = challengeBehavior.supports(expectation);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given non challenge expectation should return false")
    void given_non_challenge_expectation_should_return_false() {
      // Arrange
      ArticleInjectExpectation expectation = new ArticleInjectExpectation();

      // Act
      boolean supported = challengeBehavior.supports(expectation);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("initializeAndSaveInjectExpectationsFromExecutableInject")
  class InitializeAndSaveInjectExpectation {

    @Test
    @DisplayName(
        "given one team with one player should create result matching buildDefaultChallengeInjectExpectationResult values")
    void given_one_team_with_one_player_should_create_result_matching_challenge_defaults() {
      // Arrange
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

      ChallengeInjectExpectation template = new ChallengeInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      // Act
      challengeBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null, null);
      entityManager.flush();

      // Assert
      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());

      // Player-level expectation should have one result matching challenge defaults
      TableTopInjectExpectation playerExpectation =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() != null)
              .findFirst()
              .orElseThrow();
      assertThat(playerExpectation.getResults()).hasSize(1);

      InjectExpectationResult result = playerExpectation.getResults().getFirst();
      assertThat(result.getSourceId()).isEqualTo(CHALLENGE_SOURCE_TYPE);
      assertThat(result.getSourceType()).isEqualTo(CHALLENGE_SOURCE_TYPE);
      assertThat(result.getSourceName()).isEqualTo(CHALLENGE_SOURCE_NAME);
      assertThat(result.getResult()).isNull();
      assertThat(result.getScore()).isNull();
      assertThat(result.getDate()).isNotBlank();

      // Team-level expectation should have no results
      TableTopInjectExpectation teamExpectation =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() == null)
              .findFirst()
              .orElseThrow();
      assertThat(teamExpectation.getResults()).isEmpty();
    }
  }
}
