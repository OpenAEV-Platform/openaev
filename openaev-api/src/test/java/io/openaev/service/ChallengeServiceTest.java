package io.openaev.service;

import static io.openaev.database.model.ChallengeFlag.FLAG_TYPE.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.challenge.form.ChallengeTryInput;
import io.openaev.rest.challenge.response.ChallengeResult;
import io.openaev.rest.challenge.response.SimulationChallengesReader;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.service.challenge.ChallengeAttemptService;
import io.openaev.utils.fixtures.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChallengeServiceTest extends IntegrationTest {

  private static final String TEST_ID = "test";

  @Mock private ExerciseRepository exerciseRepository;
  @Mock private ChallengeRepository challengeRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private ChallengeAttemptService challengeAttemptService;

  private ChallengeService challengeService;

  @BeforeEach
  void setUp() {
    this.challengeService =
        new ChallengeService(
            exerciseRepository,
            challengeRepository,
            injectRepository,
            injectExpectationService,
            injectExpectationRepository,
            challengeAttemptService);
  }

  @Test
  @DisplayName("Should return a challenge result with true for type VALUE")
  void shouldTryChallengeForTypeValue() {
    // PREPARE
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(VALUE);
    flag.setValue("Test");
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("test");

    // MOCK
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));

    // EXECUTE
    ChallengeResult result = challengeService.tryChallenge("test", input);

    // VERIFY
    assertNotNull(result);
    assertTrue(result.isResult());
  }

  @Test
  @DisplayName("Should return a challenge result with false for type VALUE")
  void shouldTryChallengeForTypeValueForTypeMismatch() {
    // PREPARE
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(VALUE);
    flag.setValue("Test");
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("tests");

    // MOCK
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));

    // EXECUTE
    ChallengeResult result = challengeService.tryChallenge("test", input);

    // VERIFY
    assertNotNull(result);
    assertFalse(result.isResult());
  }

  @Test
  @DisplayName("Should return a challenge result with true for type VALUE_CASE")
  void shouldTryChallengeForTypeValueCase() {
    // PREPARE
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(VALUE_CASE);
    flag.setValue("Test");
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("Test");

    // MOCK
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));

    // EXECUTE
    ChallengeResult result = challengeService.tryChallenge("test", input);

    // VERIFY
    assertNotNull(result);
    assertTrue(result.isResult());
  }

  @Test
  @DisplayName("Should return a challenge result with false for type VALUE_CASE")
  void shouldTryChallengeForTypeValueCaseForCaseSensitive() {
    // PREPARE
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(VALUE_CASE);
    flag.setValue("Test");
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("test");

    // MOCK
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));

    // EXECUTE
    ChallengeResult result = challengeService.tryChallenge("test", input);

    // VERIFY
    assertNotNull(result);
    assertFalse(result.isResult());
  }

  @Test
  @DisplayName("Should return a challenge result with true for type REGEXP")
  void shouldTryChallengeForTypeRegexp() {
    // PREPARE
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(REGEXP);
    flag.setValue(".*\\btest\\b.*");
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("this is a test that should succeed");

    // MOCK
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));

    // EXECUTE
    ChallengeResult result = challengeService.tryChallenge("test", input);

    // VERIFY
    assertNotNull(result);
    assertTrue(result.isResult());
  }

  @Test
  @DisplayName("Should return a challenge result with false for type REGEXP")
  void shouldTryChallengeForTypeRegexpAndMismatch() {
    // PREPARE
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(REGEXP);
    flag.setValue("\btest\b");
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("this one should fail");

    // MOCK
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));

    // EXECUTE
    ChallengeResult result = challengeService.tryChallenge("test", input);

    // VERIFY
    assertNotNull(result);
    assertFalse(result.isResult());
  }

  @Test
  @DisplayName("should run player challenges and succeed")
  void shouldRunPlayerChallenges() {
    // PREPARE
    User user = UserFixture.getUser();
    user.setId("test");

    Exercise exercise = ExerciseFixture.createDefaultExercise();

    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    challenge.setId("test");

    InjectStatus status = new InjectStatus();
    status.setId("test");

    Inject inject = InjectFixture.getDefaultInject();
    inject.setStatus(status);

    ChallengeInjectExpectation expectation =
        (ChallengeInjectExpectation)
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
    expectation.setChallenge(challenge);
    expectation.setInject(inject);
    expectation.setUser(user);
    List<ChallengeInjectExpectation> expectations =
        new ArrayList<>(List.of(expectation, expectation));

    // MOCK
    when(exerciseRepository.findById("test")).thenReturn(Optional.of(exercise));
    when(injectExpectationRepository.findChallengeExpectationsByExerciseAndUser("test", "test"))
        .thenReturn(expectations);

    // EXECUTE
    SimulationChallengesReader reader = challengeService.playerChallenges("test", user);

    // VERIFY
    assertNotNull(reader);
    assertEquals(exercise.getId(), reader.getExercise().getId());

    assertNotNull(reader.getExerciseChallenges());
    assertNotNull(reader.getExerciseChallenges().getFirst());
    assertNotNull(reader.getExerciseChallenges().getFirst().getChallenge());
    assertEquals("test", reader.getExerciseChallenges().getFirst().getChallenge().getId());
  }

  @Test
  @DisplayName("should run validate challenges and succeed")
  void shouldValidateChallenges() {
    // PREPARE
    Exercise exercise = ExerciseFixture.createDefaultExercise();

    User user = UserFixture.getUser();
    user.setId("test");

    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(REGEXP);
    flag.setValue(".*\\btest\\b.*");
    flag.setChallenge(challenge);
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("this is a test that should succeed");

    InjectStatus status = new InjectStatus();
    status.setId("test");
    Inject inject = InjectFixture.getDefaultInject();
    inject.setStatus(status);
    ChallengeInjectExpectation expectation =
        (ChallengeInjectExpectation)
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
    expectation.setInject(inject);
    expectation.setChallenge(challenge);
    expectation.setUser(user);
    List<ChallengeInjectExpectation> playerExpectations = new ArrayList<>(List.of(expectation));

    // MOCK
    when(exerciseRepository.findById("test")).thenReturn(Optional.of(exercise));
    when(injectExpectationRepository.findChallengeExpectationsByExerciseAndUser("test", "test"))
        .thenReturn(playerExpectations);
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));
    when(injectExpectationRepository.findByUserAndExerciseAndChallenge(
            user.getId(), "test", "test"))
        .thenReturn(playerExpectations);
    when(challengeAttemptService.getChallengeAttempt(any())).thenReturn(Optional.empty());
    when(injectExpectationService.updateInjectExpectation(any(), (ExpectationUpdateInput) any()))
        .thenReturn(new ChallengeInjectExpectation());

    // EXECUTE
    SimulationChallengesReader reader =
        challengeService.validateChallenge("test", "test", input, user);

    // VERIFY
    assertNotNull(reader);
    assertNotNull(reader.getExercise());
    assertEquals(exercise.getName(), reader.getExercise().getName());
    assertNotNull(reader.getExerciseChallenges());
    assertNotNull(reader.getExerciseChallenges().getFirst());
    assertNotNull(reader.getExerciseChallenges().getFirst().getChallenge());
    assertEquals(
        challenge.getName(), reader.getExerciseChallenges().getFirst().getChallenge().getName());
  }

  @Test
  @DisplayName("should run validate challenges and succeed even if result is false")
  void shouldValidateChallengesEvenIfResultIsFalse() {
    // PREPARE
    Exercise exercise = ExerciseFixture.createDefaultExercise();

    User user = UserFixture.getUser();
    user.setId("test");

    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(VALUE);
    flag.setValue("Test");
    flag.setChallenge(challenge);
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("tests");

    InjectStatus status = new InjectStatus();
    status.setId("test");
    Inject inject = InjectFixture.getDefaultInject();
    inject.setStatus(status);
    ChallengeInjectExpectation expectation =
        (ChallengeInjectExpectation)
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
    expectation.setInject(inject);
    expectation.setChallenge(challenge);
    expectation.setUser(user);
    List<ChallengeInjectExpectation> playerExpectations = new ArrayList<>(List.of(expectation));

    // MOCK
    when(exerciseRepository.findById("test")).thenReturn(Optional.of(exercise));
    when(injectExpectationRepository.findChallengeExpectationsByExerciseAndUser("test", "test"))
        .thenReturn(playerExpectations);
    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));
    when(injectExpectationRepository.findByUserAndExerciseAndChallenge(
            user.getId(), "test", "test"))
        .thenReturn(playerExpectations);
    when(challengeAttemptService.getChallengeAttempt(any())).thenReturn(Optional.empty());

    // EXECUTE
    SimulationChallengesReader reader =
        challengeService.validateChallenge("test", "test", input, user);

    // VERIFY
    assertNotNull(reader);
    assertNotNull(reader.getExercise());
    assertEquals(exercise.getName(), reader.getExercise().getName());
    assertNotNull(reader.getExerciseChallenges());
    assertNotNull(reader.getExerciseChallenges().getFirst());
    assertNotNull(reader.getExerciseChallenges().getFirst().getChallenge());
    assertEquals(
        challenge.getName(), reader.getExerciseChallenges().getFirst().getChallenge().getName());
  }

  // -- MAX ATTEMPTS (player mode) --

  @ParameterizedTest
  @CsvSource({"1, 100.0", "3, 0.0"})
  @DisplayName("Should score a solved challenge against the attempt cap")
  void given_aSolvedChallenge_then_scoreItAgainstTheAttemptCap(
      int attemptsSpent, double expectedScore) {
    // -- ARRANGE --
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    User user = UserFixture.getUser();
    user.setId(TEST_ID);
    Challenge challenge = ChallengeFixture.createChallengeWithMaxAttempts(3);
    List<ChallengeInjectExpectation> expectations =
        new ArrayList<>(
            List.of(
                InjectExpectationFixture.createChallengeInjectExpectation(
                    challenge, user, TEST_ID)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("flag value");

    mockPlayerChallenge(exercise, challenge, expectations);
    when(challengeAttemptService.getChallengeAttempt(any()))
        .thenReturn(
            Optional.of(
                ChallengeAttemptFixture.createChallengeAttempt(
                    TEST_ID, TEST_ID, TEST_ID, attemptsSpent)));

    // -- ACT --
    challengeService.validateChallenge(TEST_ID, TEST_ID, input, user);

    // -- ASSERT --
    ArgumentCaptor<ExpectationUpdateInput> captor =
        ArgumentCaptor.forClass(ExpectationUpdateInput.class);
    verify(injectExpectationService).updateInjectExpectation(any(), captor.capture());
    assertEquals(expectedScore, captor.getValue().getScore());
  }

  @Test
  @DisplayName("Should count the wrong answer and fail the expectation once the cap is reached")
  void given_aWrongAnswerAtTheAttemptCap_then_countItAndFailTheExpectation() {
    // -- ARRANGE --
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    User user = UserFixture.getUser();
    user.setId(TEST_ID);
    Challenge challenge = ChallengeFixture.createChallengeWithMaxAttempts(3);
    List<ChallengeInjectExpectation> expectations =
        new ArrayList<>(
            List.of(
                InjectExpectationFixture.createChallengeInjectExpectation(
                    challenge, user, TEST_ID)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("wrong flag");

    ChallengeAttempt lastAttempt =
        ChallengeAttemptFixture.createChallengeAttempt(TEST_ID, TEST_ID, TEST_ID, 2);

    mockPlayerChallenge(exercise, challenge, expectations);
    when(challengeAttemptService.getChallengeAttempts(any())).thenReturn(List.of(lastAttempt));
    when(challengeAttemptService.getChallengeAttempt(any())).thenReturn(Optional.of(lastAttempt));

    // -- ACT --
    challengeService.validateChallenge(TEST_ID, TEST_ID, input, user);

    // -- ASSERT --
    ArgumentCaptor<List<ChallengeAttempt>> attemptsCaptor = ArgumentCaptor.captor();
    verify(challengeAttemptService).saveChallengeAttempts(attemptsCaptor.capture());
    assertEquals(3, attemptsCaptor.getValue().getFirst().getAttempt());

    ArgumentCaptor<ExpectationUpdateInput> expectationCaptor =
        ArgumentCaptor.forClass(ExpectationUpdateInput.class);
    verify(injectExpectationService).updateInjectExpectation(any(), expectationCaptor.capture());
    assertEquals(0D, expectationCaptor.getValue().getScore());
  }

  // -- PRIVATE --

  private void mockPlayerChallenge(
      Exercise exercise, Challenge challenge, List<ChallengeInjectExpectation> expectations) {
    when(exerciseRepository.findById(TEST_ID)).thenReturn(Optional.of(exercise));
    when(challengeRepository.findById(TEST_ID)).thenReturn(Optional.of(challenge));
    when(injectExpectationRepository.findByUserAndExerciseAndChallenge(TEST_ID, TEST_ID, TEST_ID))
        .thenReturn(expectations);
    when(injectExpectationRepository.findChallengeExpectationsByExerciseAndUser(TEST_ID, TEST_ID))
        .thenReturn(expectations);
  }
}
