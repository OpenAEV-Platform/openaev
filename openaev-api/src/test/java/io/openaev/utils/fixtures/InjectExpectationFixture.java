package io.openaev.utils.fixtures;

import io.openaev.database.model.*;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import jakarta.annotation.Nullable;
import java.util.Map;

public class InjectExpectationFixture {

  static Long EXPIRATION_TIME_SIX_HOURS = 21600L;
  static Long EXPIRATION_TIME_ONE_HOUR = 3600L;

  static Double EXPECTED_SCORE = 100.0;

  public static BaseInjectExpectation createExpectationWithTypeAndStatus(
      BaseInjectExpectation.EXPECTATION_TYPE type,
      BaseInjectExpectation.EXPECTATION_STATUS status) {
    BaseInjectExpectation expectation = createExpectationForType(type);
    expectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    expectation.setExpectedScore(EXPECTED_SCORE);
    switch (status) {
      case SUCCESS -> expectation.setScore(EXPECTED_SCORE);
      case FAILED -> expectation.setScore(0.0);
      case PENDING -> expectation.setScore(null);
      case PARTIAL -> expectation.setScore(EXPECTED_SCORE / 2);
      default -> throw new IllegalArgumentException("Invalid status: " + status);
    }
    return expectation;
  }

  public static PreventionInjectExpectation createPreventionInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    PreventionInjectExpectation expectation = new PreventionInjectExpectation();
    expectation.setInject(inject);
    expectation.setAgent(agent);
    expectation.setExpectedScore(EXPECTED_SCORE);
    expectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return expectation;
  }

  public static DetectionInjectExpectation createDetectionInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    DetectionInjectExpectation expectation = createDefaultDetectionInjectExpectation();
    expectation.setInject(inject);
    expectation.setAgent(agent);
    if (agent != null) {
      expectation.setAsset(agent.getAsset());
    }
    return expectation;
  }

  public static VulnerabilityInjectExpectation createVulnerabilityInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    VulnerabilityInjectExpectation expectation = new VulnerabilityInjectExpectation();
    expectation.setInject(inject);
    expectation.setAgent(agent);
    expectation.setExpectedScore(EXPECTED_SCORE);
    expectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return expectation;
  }

  public static ManualInjectExpectation createManualInjectExpectation(Team team, Inject inject) {
    ManualInjectExpectation expectation = new ManualInjectExpectation();
    expectation.setInject(inject);
    expectation.setTeam(team);
    expectation.setExpectedScore(EXPECTED_SCORE);
    expectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return expectation;
  }

  public static ArticleInjectExpectation createArticleInjectExpectation(Team team, Inject inject) {
    ArticleInjectExpectation expectation = new ArticleInjectExpectation();
    expectation.setInject(inject);
    expectation.setTeam(team);
    expectation.setExpectedScore(EXPECTED_SCORE);
    expectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return expectation;
  }

  public static ManualInjectExpectation createManualInjectExpectationWithExercise(
      Team team, Inject inject, Exercise exercise, String expectationName) {
    ManualInjectExpectation expectation = new ManualInjectExpectation();
    expectation.setInject(inject);
    expectation.setTeam(team);
    expectation.setExpectedScore(EXPECTED_SCORE);
    expectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    expectation.setExercise(exercise);
    expectation.setName(expectationName);
    return expectation;
  }

  public static DetectionInjectExpectation createDefaultDetectionInjectExpectation() {
    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setExpectedScore(EXPECTED_SCORE);
    expectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return expectation;
  }

  public static InjectExpectationUpdateInput getInjectExpectationUpdateInput(
      String collectorId, String result, boolean isSuccess) {
    return InjectExpectationUpdateInput.builder()
        .collectorId(collectorId)
        .result(result)
        .isSuccess(isSuccess)
        .metadata(Map.of("alertId", "alertId"))
        .build();
  }

  private static BaseInjectExpectation createExpectationForType(
      BaseInjectExpectation.EXPECTATION_TYPE type) {
    return switch (type) {
      case DETECTION -> new DetectionInjectExpectation();
      case PREVENTION -> new PreventionInjectExpectation();
      case VULNERABILITY -> new VulnerabilityInjectExpectation();
      case MANUAL -> new ManualInjectExpectation();
      case ARTICLE -> new ArticleInjectExpectation();
      case CHALLENGE -> new ChallengeInjectExpectation();
    };
  }
}
