package io.openaev.expectation;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.*;

import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.model.inject.form.Expectation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExpectationBuilderService {

  public static final String PREVENTION_NAME = "Prevention";
  public static final String DETECTION_NAME = "Detection";
  public static final String VULNERABILITY_NAME = "Vulnerability";
  public static final String CHALLENGE_NAME = "Expect targets to complete the challenge(s)";
  public static final String ARTICLE_NAME = "Expect targets to read the article(s)";
  public static final String TEXT_NAME = "Simple expectation";
  public static final String MANUAL_NAME = "Manual expectation";
  public static final String DOCUMENT_NAME = "A document must be sent / uploaded";

  public static final Double DEFAULT_EXPECTATION_SCORE = 100.0;

  private final ExpectationPropertiesConfig expectationPropertiesConfig;

  public Expectation buildPreventionExpectation() {
    return buildExpectation(
        PREVENTION, PREVENTION_NAME, expectationPropertiesConfig.getPreventionExpirationTime());
  }

  public Expectation buildDetectionExpectation() {
    return buildExpectation(
        DETECTION, DETECTION_NAME, expectationPropertiesConfig.getDetectionExpirationTime());
  }

  public Expectation buildVulnerabilityExpectation() {
    return buildExpectation(
        VULNERABILITY,
        VULNERABILITY_NAME,
        expectationPropertiesConfig.getVulnerabilityExpirationTime());
  }

  public Expectation buildChallengeExpectation() {
    return buildExpectation(
        CHALLENGE, CHALLENGE_NAME, expectationPropertiesConfig.getChallengeExpirationTime());
  }

  public Expectation buildArticleExpectation() {
    return buildExpectation(
        ARTICLE, ARTICLE_NAME, expectationPropertiesConfig.getArticleExpirationTime());
  }

  public Expectation buildTextExpectation() {
    return buildExpectation(TEXT, TEXT_NAME, expectationPropertiesConfig.getManualExpirationTime());
  }

  public Expectation buildManualExpectation() {
    return buildExpectation(
        MANUAL, MANUAL_NAME, expectationPropertiesConfig.getManualExpirationTime());
  }

  public Expectation buildDocumentExpectation() {
    return buildExpectation(
        DOCUMENT, DOCUMENT_NAME, expectationPropertiesConfig.getManualExpirationTime());
  }

  private Expectation buildExpectation(EXPECTATION_TYPE type, String name, long expirationTime) {
    Expectation expectation = new Expectation();
    expectation.setType(type);
    expectation.setName(name);
    expectation.setScore(DEFAULT_EXPECTATION_SCORE);
    expectation.setExpirationTime(expirationTime);
    return expectation;
  }
}
