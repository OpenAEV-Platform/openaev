package io.openaev.service;

import static io.openaev.database.model.ChallengeFlag.FLAG_TYPE.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Challenge;
import io.openaev.database.model.ChallengeFlag;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.challenge.form.ChallengeTryInput;
import io.openaev.rest.challenge.form.FlagInput;
import io.openaev.rest.challenge.response.ChallengeResult;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.service.challenge.ChallengeAttemptService;
import io.openaev.utils.fixtures.ChallengeFixture;
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
public class ChallengeServiceFlagValidationTest {

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

  private FlagInput buildFlagInput(String type, String value) {
    FlagInput flagInput = new FlagInput();
    flagInput.setType(type);
    flagInput.setValue(value);
    return flagInput;
  }

  @Test
  @DisplayName("Should accept a REGEXP flag with a valid pattern")
  void shouldAcceptValidRegexpFlag() {
    List<FlagInput> flags = List.of(buildFlagInput(REGEXP.name(), ".*\\btest\\b.*"));

    assertDoesNotThrow(() -> challengeService.validateFlags(flags));
  }

  @Test
  @DisplayName("Should reject a REGEXP flag with an invalid pattern")
  void shouldRejectInvalidRegexpFlag() {
    List<FlagInput> flags = List.of(buildFlagInput(REGEXP.name(), "[unclosed"));

    InputValidationException exception =
        assertThrows(InputValidationException.class, () -> challengeService.validateFlags(flags));
    assertEquals("challenge_flags", exception.getField());
    assertTrue(exception.getMessage().contains("Invalid regular expression"));
  }

  @Test
  @DisplayName("Should not check regex syntax for VALUE flags")
  void shouldIgnoreValueFlags() {
    // An invalid pattern is fine on a VALUE flag: it is matched literally
    List<FlagInput> flags = List.of(buildFlagInput(VALUE.name(), "[unclosed"));

    assertDoesNotThrow(() -> challengeService.validateFlags(flags));
  }

  @Test
  @DisplayName("Should treat an invalid stored REGEXP pattern as non-matching")
  void shouldReturnFalseForInvalidStoredPattern() {
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    ChallengeFlag flag = ChallengeFixture.createDefaultChallengeFlag();
    flag.setType(REGEXP);
    flag.setValue("[unclosed");
    challenge.setFlags(new ArrayList<>(List.of(flag)));

    ChallengeTryInput input = new ChallengeTryInput();
    input.setValue("any answer");

    when(challengeRepository.findById("test")).thenReturn(Optional.of(challenge));

    ChallengeResult result = assertDoesNotThrow(() -> challengeService.tryChallenge("test", input));
    assertNotNull(result);
    assertFalse(result.isResult());
  }
}
