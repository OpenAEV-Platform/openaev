package io.openaev.service;

import static io.openaev.utils.challenge.ChallengeAttemptUtils.buildChallengeAttemptID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.ChallengeInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.User;
import io.openaev.database.repository.ChallengeAttemptRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.challenge.form.ChallengeTryInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("ChallengeService scores a challenge against its attempt cap in player mode")
class ChallengeServiceMaxAttemptsTest extends IntegrationTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final String RIGHT_FLAG = "flag value";
  private static final String WRONG_FLAG = "wrong flag";

  @Autowired private ChallengeService challengeService;
  @Autowired private ChallengeAttemptRepository challengeAttemptRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  @Autowired private ChallengeComposer challengeComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TeamComposer teamComposer;

  private Challenge challenge;
  private Exercise exercise;
  private User player;
  private InjectStatus injectStatus;
  private ChallengeInjectExpectation expectation;

  @BeforeEach
  void seedAChallengePublishedToOnePlayer() {
    challengeComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    injectExpectationComposer.reset();
    userComposer.reset();
    teamComposer.reset();

    challenge = ChallengeFixture.createChallengeWithMaxAttempts(MAX_ATTEMPTS);
    challengeComposer.forChallenge(challenge).persist();

    player = UserFixture.getUser();
    exercise = ExerciseFixture.createDefaultExercise();
    injectStatus = InjectStatusFixture.createSuccessStatus();
    expectation =
        InjectExpectationFixture.createChallengeInjectExpectation(challenge, player, exercise);

    injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withExercise(exerciseComposer.forExercise(exercise))
        .withInjectStatus(injectStatusComposer.forInjectStatus(injectStatus))
        .withExpectation(
            injectExpectationComposer
                .forExpectation(expectation)
                .withUser(userComposer.forUser(player))
                .withTeam(teamComposer.forTeam(TeamFixture.getTeam(player))))
        .persist();
  }

  @ParameterizedTest
  @CsvSource({"1, 100.0", "3, 0.0"})
  @DisplayName("Should award the expected score within the cap, and zero past it")
  void given_aSolvedChallenge_then_scoreItAgainstTheAttemptCap(
      int attemptsSpent, double expectedScore) {
    // -- ARRANGE --
    spendAttempts(attemptsSpent);
    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue(RIGHT_FLAG);

    // -- ACT --
    challengeService.validateChallenge(exercise.getId(), challenge.getId(), input, player);

    // -- ASSERT --
    assertEquals(expectedScore, readExpectationScore());
  }

  @Test
  @DisplayName("Should count the wrong answer and fail the expectation once the cap is reached")
  void given_aWrongAnswerAtTheAttemptCap_then_countItAndFailTheExpectation() {
    // -- ARRANGE --
    spendAttempts(MAX_ATTEMPTS - 1);
    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue(WRONG_FLAG);

    // -- ACT --
    challengeService.validateChallenge(exercise.getId(), challenge.getId(), input, player);

    // -- ASSERT --
    assertEquals(MAX_ATTEMPTS, readAttempt());
    assertEquals(0D, readExpectationScore());
  }

  // -- PRIVATE --

  private void spendAttempts(int attempts) {
    challengeAttemptRepository.save(
        ChallengeAttemptFixture.createChallengeAttempt(
            challenge.getId(), injectStatus.getId(), player.getId(), attempts));
  }

  private int readAttempt() {
    return challengeAttemptRepository
        .findById(buildChallengeAttemptID(challenge.getId(), injectStatus.getId(), player.getId()))
        .orElseThrow()
        .getAttempt();
  }

  private Double readExpectationScore() {
    return injectExpectationRepository.findById(expectation.getId()).orElseThrow().getScore();
  }
}
