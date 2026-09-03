package io.openaev.utils.fixtures;

import io.openaev.database.model.*;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import java.time.Instant;

public class ExpectationFixture {

  static Double SCORE = 100.0;

  public static Expectation createExpectation(
      BaseInjectExpectation.EXPECTATION_TYPE expectationType, String expectationName) {
    Expectation expectation = new Expectation();
    expectation.setExpectationGroup(false);
    expectation.setName(expectationName);
    expectation.setDescription("Expectation 1");
    expectation.setType(expectationType);
    expectation.setScore(10D);
    expectation.setExpirationTime(Instant.now().toEpochMilli());
    return expectation;
  }

  public static Expectation createExpectation(
      BaseInjectExpectation.EXPECTATION_TYPE expectationType) {
    return createExpectation(expectationType, "Expectation 1");
  }

  public static Expectation createExpectation() {
    Expectation expectation = new Expectation();
    expectation.setScore(SCORE);
    expectation.setName("Expectation Name");
    expectation.setDescription("Expectation Description");
    expectation.setExpirationTime(60L);
    return expectation;
  }

  public static ExpectationUpdateInput getExpectationUpdateInput(String sourceId, Double score) {
    return ExpectationUpdateInput.builder()
        .sourceId(sourceId)
        .sourceName("security-platform-name")
        .sourceType("security-platform-type")
        .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
        .score(score)
        .build();
  }

  // -- DETECTION EXPECTATION --

  public static ArticleInjectExpectation createArticleInjectExpectationForPlayer(
      Team team, User user, String name) {
    ArticleInjectExpectation articleInjectExpectation = new ArticleInjectExpectation();
    articleInjectExpectation.setUser(user);
    articleInjectExpectation.setTeam(team);
    articleInjectExpectation.setName(name);
    articleInjectExpectation.setDescription("Article Inject Expectation Description");
    articleInjectExpectation.setExpectedScore(SCORE);
    return articleInjectExpectation;
  }
}
