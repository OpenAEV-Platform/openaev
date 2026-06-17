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
    BaseInjectExpectation BaseInjectExpectation = new BaseInjectExpectation();
    BaseInjectExpectation.setInject(inject);
    BaseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION);
    BaseInjectExpectation.setAgent(agent);
    BaseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    BaseInjectExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return BaseInjectExpectation;
  }

  public static BaseInjectExpectation createDetectionInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    BaseInjectExpectation BaseInjectExpectation = createDefaultDetectionInjectExpectation();
    BaseInjectExpectation.setInject(inject);
    BaseInjectExpectation.setAgent(agent);
    return BaseInjectExpectation;
  }

  public static BaseInjectExpectation createVulnerabilityInjectExpectation(
      Inject inject, @Nullable Agent agent) {
    BaseInjectExpectation BaseInjectExpectation = new BaseInjectExpectation();
    BaseInjectExpectation.setInject(inject);
    BaseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY);
    BaseInjectExpectation.setAgent(agent);
    BaseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    BaseInjectExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return BaseInjectExpectation;
  }

  public static BaseInjectExpectation createManualInjectExpectation(Team team, Inject inject) {
    BaseInjectExpectation BaseInjectExpectation = new BaseInjectExpectation();
    BaseInjectExpectation.setInject(inject);
    BaseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL);
    BaseInjectExpectation.setTeam(team);
    BaseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    BaseInjectExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return BaseInjectExpectation;
  }

  public static BaseInjectExpectation createArticleInjectExpectation(Team team, Inject inject) {
    BaseInjectExpectation BaseInjectExpectation = new BaseInjectExpectation();
    BaseInjectExpectation.setInject(inject);
    BaseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE);
    BaseInjectExpectation.setTeam(team);
    BaseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    BaseInjectExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return BaseInjectExpectation;
  }

  public static BaseInjectExpectation createManualInjectExpectationWithExercise(
      Team team, Inject inject, Exercise exercise, String expectationName) {
    BaseInjectExpectation BaseInjectExpectation = new BaseInjectExpectation();
    BaseInjectExpectation.setInject(inject);
    BaseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL);
    BaseInjectExpectation.setTeam(team);
    BaseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    BaseInjectExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    BaseInjectExpectation.setExercise(exercise);
    BaseInjectExpectation.setName(expectationName);
    return BaseInjectExpectation;
  }

  public static BaseInjectExpectation createDefaultDetectionInjectExpectation() {
    BaseInjectExpectation BaseInjectExpectation = new BaseInjectExpectation();
    BaseInjectExpectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.DETECTION);
    BaseInjectExpectation.setExpectedScore(EXPECTED_SCORE);
    BaseInjectExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return BaseInjectExpectation;
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
