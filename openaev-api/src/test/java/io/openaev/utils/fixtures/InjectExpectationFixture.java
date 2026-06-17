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
    BaseInjectExpectation expectation = new BaseInjectExpectation();
    expectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    expectation.setType(type);
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

  public static BaseInjectExpectation createPreventionInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    BaseInjectExpectation baseInjectExpectation = new BaseInjectExpectation();
    baseInjectExpectation.setInject(inject);
    baseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION);
    baseInjectExpectation.setAgent(agent);
    baseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    baseInjectExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return baseInjectExpectation;
  }

  public static BaseInjectExpectation createDetectionInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    BaseInjectExpectation baseInjectExpectation = createDefaultDetectionInjectExpectation();
    baseInjectExpectation.setInject(inject);
    baseInjectExpectation.setAgent(agent);
    return baseInjectExpectation;
  }

  public static BaseInjectExpectation createVulnerabilityInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    BaseInjectExpectation baseInjectExpectation = new BaseInjectExpectation();
    baseInjectExpectation.setInject(inject);
    baseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY);
    baseInjectExpectation.setAgent(agent);
    baseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    baseInjectExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return baseInjectExpectation;
  }

  public static BaseInjectExpectation createManualInjectExpectation(Team team, Inject inject) {
    BaseInjectExpectation baseInjectExpectation = new BaseInjectExpectation();
    baseInjectExpectation.setInject(inject);
    baseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL);
    baseInjectExpectation.setTeam(team);
    baseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    baseInjectExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return baseInjectExpectation;
  }

  public static BaseInjectExpectation createArticleInjectExpectation(Team team, Inject inject) {
    BaseInjectExpectation baseInjectExpectation = new BaseInjectExpectation();
    baseInjectExpectation.setInject(inject);
    baseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE);
    baseInjectExpectation.setTeam(team);
    baseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    baseInjectExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return baseInjectExpectation;
  }

  public static BaseInjectExpectation createManualInjectExpectationWithExercise(
      Team team, Inject inject, Exercise exercise, String expectationName) {
    BaseInjectExpectation baseInjectExpectation = new BaseInjectExpectation();
    baseInjectExpectation.setInject(inject);
    baseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL);
    baseInjectExpectation.setTeam(team);
    baseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    baseInjectExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    baseInjectExpectation.setExercise(exercise);
    baseInjectExpectation.setName(expectationName);
    return baseInjectExpectation;
  }

  public static BaseInjectExpectation createDefaultDetectionInjectExpectation() {
    BaseInjectExpectation baseInjectExpectation = new BaseInjectExpectation();
    baseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.DETECTION);
    baseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    baseInjectExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return baseInjectExpectation;
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
}
