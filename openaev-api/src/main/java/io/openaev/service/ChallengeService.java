package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openaev.utils.challenge.ChallengeAttemptUtils.buildChallengeAttempt;
import static io.openaev.utils.challenge.ChallengeAttemptUtils.buildChallengeAttemptID;
import static io.openaev.utils.challenge.ChallengeExpectationUtils.buildChallengeUpdateInput;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.rest.challenge.form.ChallengeTryInput;
import io.openaev.rest.challenge.response.ChallengeInformation;
import io.openaev.rest.challenge.response.ChallengeResult;
import io.openaev.rest.challenge.response.SimulationChallengesReader;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.service.challenge.ChallengeAttemptService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChallengeService {

  private final ExerciseRepository exerciseRepository;
  private final ChallengeRepository challengeRepository;
  private final InjectRepository injectRepository;
  private final InjectExpectationService injectExpectationService;
  private final InjectExpectationRepository injectExpectationRepository;
  private final ChallengeAttemptService challengeAttemptService;
  @Resource protected ObjectMapper mapper;

  public Challenge enrichChallengeWithExercisesOrScenarios(@NotNull Challenge challenge) {
    List<Inject> injects =
        fromIterable(this.injectRepository.findAllForChallengeId("%" + challenge.getId() + "%"));
    List<String> exerciseIds =
        injects.stream()
            .filter(i -> i.getExercise() != null)
            .map(i -> i.getExercise().getId())
            .distinct()
            .toList();
    challenge.setExerciseIds(exerciseIds);
    List<String> scenarioIds =
        injects.stream()
            .filter(i -> i.getScenario() != null)
            .map(i -> i.getScenario().getId())
            .distinct()
            .toList();
    challenge.setScenarioIds(scenarioIds);
    return challenge;
  }

  public Iterable<Challenge> getExerciseChallenges(@NotBlank final String exerciseId) {
    Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
    return StreamSupport.stream(getInjectsChallenges(exercise.getInjects()).spliterator(), false)
        .map(this::enrichChallengeWithExercisesOrScenarios)
        .toList();
  }

  public Iterable<Challenge> getScenarioChallenges(@NotNull final Scenario scenario) {
    return StreamSupport.stream(getInjectsChallenges(scenario.getInjects()).spliterator(), false)
        .map(this::enrichChallengeWithExercisesOrScenarios)
        .toList();
  }

  public Iterable<Challenge> getInjectsChallenges(@NotNull final List<Inject> injects) {
    return resolveChallenges(injects).toList();
  }

  public ChallengeResult tryChallenge(String challengeId, ChallengeTryInput input) {
    Challenge challenge =
        challengeRepository
            .findById(challengeId)
            .orElseThrow(() -> new ElementNotFoundException("Challenge not found"));
    for (ChallengeFlag flag : challenge.getFlags()) {
      if (checkFlag(flag, input.getValue())) {
        return new ChallengeResult(true);
      }
    }
    return new ChallengeResult(false);
  }

  public SimulationChallengesReader playerChallenges(String exerciseId, User user) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    SimulationChallengesReader reader = new SimulationChallengesReader(exercise);
    List<InjectExpectation> challengeExpectations =
        injectExpectationRepository.findChallengeExpectationsByExerciseAndUser(
            exerciseId, user.getId());

    // Filter expectations by unique challenge
    Set<String> seenChallenges = new HashSet<>();
    List<InjectExpectation> distinctExpectations = new ArrayList<>();

    for (InjectExpectation expectation : challengeExpectations) {
      String challengeId = expectation.getChallenge().getId();
      if (!seenChallenges.contains(challengeId)) {
        seenChallenges.add(challengeId);
        distinctExpectations.add(expectation);
      }
    }

    List<ChallengeInformation> challenges =
        distinctExpectations.stream()
            .map(
                injectExpectation -> {
                  Challenge challenge = injectExpectation.getChallenge();
                  challenge.setVirtualPublication(injectExpectation.getCreatedAt());
                  InjectStatus injectStatus =
                      injectExpectation
                          .getInject()
                          .getStatus()
                          .orElseThrow(() -> new ElementNotFoundException("Status should exist"));
                  ChallengeAttemptId challengeAttemptId =
                      buildChallengeAttemptID(
                          challenge.getId(), injectStatus.getId(), user.getId());
                  ChallengeAttempt challengeAttempt =
                      this.challengeAttemptService
                          .getChallengeAttempt(challengeAttemptId)
                          .orElse(buildChallengeAttempt(challengeAttemptId));
                  return new ChallengeInformation(
                      challenge, injectExpectation, challengeAttempt.getAttempt());
                })
            .sorted(Comparator.comparing(o -> o.getChallenge().getVirtualPublication()))
            .toList();
    reader.setExerciseChallenges(challenges);
    return reader;
  }

  public SimulationChallengesReader validateChallenge(
      String exerciseId, String challengeId, ChallengeTryInput input, User user) {
    ChallengeResult challengeResult = tryChallenge(challengeId, input);
    if (challengeResult.isResult()) {
      // Success: Find and update the user's expectations and challenge attempt
      List<InjectExpectation> playerExpectations =
          injectExpectationRepository.findByUserAndExerciseAndChallenge(
              user.getId(), exerciseId, challengeId);
      playerExpectations.forEach(
          playerExpectation -> {
            InjectStatus injectStatus =
                playerExpectation
                    .getInject()
                    .getStatus()
                    .orElseThrow(() -> new ElementNotFoundException("Status should exist"));
            ChallengeAttemptId challengeAttemptId =
                buildChallengeAttemptID(challengeId, injectStatus.getId(), user.getId());
            ChallengeAttempt challengeAttempt =
                this.challengeAttemptService
                    .getChallengeAttempt(challengeAttemptId)
                    .orElse(buildChallengeAttempt(challengeAttemptId));
            // Adjust the score based on the current attempt number
            double score =
                playerExpectation.getChallenge().getMaxAttempts() == null
                        || challengeAttempt.getAttempt()
                            < playerExpectation.getChallenge().getMaxAttempts()
                    ? playerExpectation.getExpectedScore()
                    : 0;

            ExpectationUpdateInput expectationUpdateInput = buildChallengeUpdateInput(score);
            this.injectExpectationService.updateInjectExpectation(
                playerExpectation.getId(), expectationUpdateInput);
          });
    } else {
      // Failure: Find and update the user's challenge attempt
      List<InjectExpectation> playerExpectations =
          injectExpectationRepository.findByUserAndExerciseAndChallenge(
              user.getId(), exerciseId, challengeId);
      List<String> injectStatusIds =
          playerExpectations.stream()
              .map(
                  e ->
                      e.getInject()
                          .getStatus()
                          .orElseThrow(() -> new ElementNotFoundException("Status should exist"))
                          .getId())
              .toList();
      Map<ChallengeAttemptId, InjectExpectation> expectationMap = new HashMap<>();
      List<ChallengeAttemptId> challengeAttemptIds = new ArrayList<>();
      for (int i = 0; i < playerExpectations.size(); i++) {
        InjectExpectation expectation = playerExpectations.get(i);
        String injectStatusId = injectStatusIds.get(i);
        ChallengeAttemptId challengeAttemptId =
            buildChallengeAttemptID(challengeId, injectStatusId, user.getId());
        expectationMap.put(challengeAttemptId, expectation);
        challengeAttemptIds.add(challengeAttemptId);
      }
      List<ChallengeAttempt> challengeAttempts =
          challengeAttemptService.getChallengeAttempts(challengeAttemptIds);
      List<ChallengeAttempt> attemptsToSave = new ArrayList<>();
      Map<String, ExpectationUpdateInput> expectationsToUpdate = new HashMap<>();
      for (ChallengeAttemptId id : challengeAttemptIds) {
        InjectExpectation expectation = expectationMap.get(id);
        ChallengeAttempt attempt =
            challengeAttempts.stream()
                .filter(ca -> ca.getCompositeId().equals(id))
                .findFirst()
                .orElse(buildChallengeAttempt(id));

        attempt.setAttempt(attempt.getAttempt() + 1);
        attemptsToSave.add(attempt);

        if (expectation.getChallenge().getMaxAttempts() != null
            && attempt.getAttempt() >= expectation.getChallenge().getMaxAttempts()) {
          expectationsToUpdate.put(expectation.getId(), buildChallengeUpdateInput(0D));
        }
      }

      challengeAttemptService.saveChallengeAttempts(attemptsToSave);
      expectationsToUpdate.forEach(injectExpectationService::updateInjectExpectation);
    }
    return playerChallenges(exerciseId, user);
  }

  // -- PRIVATE --
  private Stream<Challenge> resolveChallenges(@NotNull final List<Inject> injects) {
    List<String> challenges =
        injects.stream()
            .filter(
                inject ->
                    inject
                        .getInjectorContract()
                        .map(contract -> contract.getId().equals(CHALLENGE_PUBLISH))
                        .orElse(false))
            .filter(inject -> inject.getContent() != null)
            .flatMap(
                inject -> {
                  try {
                    ChallengeContent content =
                        mapper.treeToValue(inject.getContent(), ChallengeContent.class);
                    return content.getChallenges().stream();
                  } catch (JsonProcessingException e) {
                    return Stream.empty();
                  }
                })
            .distinct()
            .toList();

    return fromIterable(this.challengeRepository.findAllById(challenges)).stream();
  }

  private boolean checkFlag(ChallengeFlag flag, String value) {
    switch (flag.getType()) {
      case VALUE -> {
        return value.equalsIgnoreCase(flag.getValue());
      }
      case VALUE_CASE -> {
        return value.equals(flag.getValue());
      }
      case REGEXP -> {
        return Pattern.compile(flag.getValue()).matcher(value).matches();
      }
      default -> {
        return false;
      }
    }
  }
}
