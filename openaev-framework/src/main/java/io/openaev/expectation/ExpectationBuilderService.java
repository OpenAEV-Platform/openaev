package io.openaev.expectation;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.model.inject.form.Expectation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for building pre-configured expectation instances.
 *
 * <p>This service provides factory methods for creating expectations with default configurations.
 * Each expectation type has appropriate default names and expiration times based on the platform
 * configuration.
 *
 * <p>Supported expectation types:
 *
 * <ul>
 *   <li><b>Technical:</b> Prevention, Detection, Vulnerability
 *   <li><b>Human:</b> Challenge, Article, Manual, Document, Text
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * Expectation prevention = expectationBuilderService.buildPreventionExpectation();
 * Expectation detection = expectationBuilderService.buildDetectionExpectation();
 * }</pre>
 *
 * @see ExpectationPropertiesConfig for expiration time configuration
 * @see io.openaev.model.Expectation
 */
@RequiredArgsConstructor
@Service
public class ExpectationBuilderService {

  // Default expectation names
  /** Default name for prevention expectations. */
  public static final String PREVENTION_NAME = "Prevention";

  /** Default name for detection expectations. */
  public static final String DETECTION_NAME = "Detection";

  /** Default name for vulnerability expectations. */
  public static final String VULNERABILITY_NAME = "Vulnerability";

  /** Default name for challenge expectations. */
  public static final String CHALLENGE_NAME = "Expect targets to complete the challenge(s)";

  /** Default name for article/channel expectations. */
  public static final String ARTICLE_NAME = "Expect targets to read the article(s)";

  /** Default name for manual expectations. */
  public static final String MANUAL_NAME = "Manual expectation";

  /** Default score for all expectations (100%). */
  public static final Double DEFAULT_EXPECTATION_SCORE = 100.0;

  private final ExpectationPropertiesConfig expectationPropertiesConfig;

  /**
   * Builds a prevention expectation with default configuration.
   *
   * @return a configured prevention expectation
   */
  public Expectation buildPreventionExpectation() {
    return buildExpectation(
        PREVENTION,
        PREVENTION_NAME,
        expectationPropertiesConfig.getPreventionExpirationTime(),
        false,
        false);
  }

  /**
   * Builds a detection expectation with default configuration.
   *
   * @return a configured detection expectation
   */
  public Expectation buildDetectionExpectation() {
    return buildExpectation(
        DETECTION,
        DETECTION_NAME,
        expectationPropertiesConfig.getDetectionExpirationTime(),
        false,
        false);
  }

  /**
   * Builds a vulnerability expectation with default configuration.
   *
   * @return a configured vulnerability expectation
   */
  public Expectation buildVulnerabilityExpectation() {
    return buildExpectation(
        VULNERABILITY,
        VULNERABILITY_NAME,
        expectationPropertiesConfig.getVulnerabilityExpirationTime(),
        false,
        false);
  }

  /**
   * Builds a challenge expectation with default configuration.
   *
   * @return a configured challenge expectation
   */
  public Expectation buildPredefinedChallengeExpectation() {
    return buildExpectation(
        CHALLENGE,
        CHALLENGE_NAME,
        expectationPropertiesConfig.getChallengeExpirationTime(),
        false,
        true);
  }

  /**
   * Builds an article/channel expectation with default configuration.
   *
   * @return a configured article expectation
   */
  public Expectation buildPredefinedArticleExpectation() {
    return buildExpectation(
        ARTICLE, ARTICLE_NAME, expectationPropertiesConfig.getArticleExpirationTime(), false, true);
  }

  /**
   * Builds a manual expectation with default configuration MANUAL expectations are not limited (can
   * be added multiple times).
   *
   * @return a configured manual expectation
   */
  public Expectation buildManualExpectation() {
    return buildExpectation(
        MANUAL, MANUAL_NAME, expectationPropertiesConfig.getManualExpirationTime(), true, false);
  }

  /**
   * Internal helper to build an expectation with the specified parameters.
   *
   * @param type the expectation type
   * @param name the display name
   * @param expirationTime the expiration time in seconds
   * @param multiSelectable whether the expectation can be added multiple times to the same inject
   * @return a configured expectation
   */
  private Expectation buildExpectation(
      EXPECTATION_TYPE type,
      String name,
      long expirationTime,
      boolean multiSelectable,
      boolean isPredefined) {
    Expectation expectation = new Expectation();
    expectation.setType(type);
    expectation.setName(name);
    expectation.setScore(DEFAULT_EXPECTATION_SCORE);
    expectation.setExpirationTime(expirationTime);
    expectation.setMultiSelectable(multiSelectable);
    expectation.setPredefined(isPredefined);
    return expectation;
  }
}
