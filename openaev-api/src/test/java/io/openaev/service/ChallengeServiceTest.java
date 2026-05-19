package io.openaev.service;

import static io.openaev.database.model.ChallengeFlag.FLAG_TYPE.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChallengeServiceTest extends IntegrationTest {

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

    InjectExpectation expectation =
        InjectExpectationFixture.createExpectationWithTypeAndStatus(
            InjectExpectation.EXPECTATION_TYPE.DETECTION,
            InjectExpectation.EXPECTATION_STATUS.SUCCESS);
    expectation.setChallenge(challenge);
    expectation.setInject(inject);
    List<InjectExpectation> expectations = new ArrayList<>(List.of(expectation, expectation));

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
    InjectExpectation expectation =
        InjectExpectationFixture.createExpectationWithTypeAndStatus(
            InjectExpectation.EXPECTATION_TYPE.DETECTION,
            InjectExpectation.EXPECTATION_STATUS.SUCCESS);
    expectation.setInject(inject);
    expectation.setChallenge(challenge);
    List<InjectExpectation> playerExpectations = new ArrayList<>(List.of(expectation));

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
        .thenReturn(new InjectExpectation());

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
    InjectExpectation expectation =
        InjectExpectationFixture.createExpectationWithTypeAndStatus(
            InjectExpectation.EXPECTATION_TYPE.DETECTION,
            InjectExpectation.EXPECTATION_STATUS.SUCCESS);
    expectation.setInject(inject);
    expectation.setChallenge(challenge);
    List<InjectExpectation> playerExpectations = new ArrayList<>(List.of(expectation));

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
}
