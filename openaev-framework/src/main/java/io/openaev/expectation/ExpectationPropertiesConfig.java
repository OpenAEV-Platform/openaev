package io.openaev.expectation;

import static java.util.Optional.ofNullable;

import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for expectation expiration times and default scores.
 *
 * <p>This component provides configurable expiration times for different expectation types and
 * default score values. The configuration follows a hierarchy where specific values override
 * category defaults:
 *
 * <ul>
 *   <li><b>Technical expectations</b> (detection, prevention, vulnerability): Default 6 hours
 *   <li><b>Human expectations</b> (challenge, article, manual, document, text): Default 24 hours
 * </ul>
 *
 * <p>Configuration can be provided via properties with either {@code openbas.expectation.*} or
 * {@code openaev.expectation.*} prefixes for backward compatibility.
 */
@Component
@Setter
@Slf4j
public class ExpectationPropertiesConfig {

  /** Default expiration time for technical expectations (6 hours in seconds). */
  public static final long DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME = 21600L;

  /** Default expiration time for human expectations (24 hours in seconds). */
  public static final long DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME = 86400L;

  /** Default score for manual expectations (0-100 range). */
  public static final int DEFAULT_MANUAL_EXPECTATION_SCORE = 50;

  /** Minimum valid score value. */
  private static final int MIN_SCORE = 0;

  /** Maximum valid score value. */
  private static final int MAX_SCORE = 100;

  @Value(
      "${openbas.expectation.technical.expiration-time:${openaev.expectation.technical.expiration-time:#{null}}}")
  private Long technicalExpirationTime;

  @Value(
      "${openbas.expectation.detection.expiration-time:${openaev.expectation.detection.expiration-time:#{null}}}")
  private Long detectionExpirationTime;

  @Value(
      "${openbas.expectation.prevention.expiration-time:${openaev.expectation.prevention.expiration-time:#{null}}}")
  private Long preventionExpirationTime;

  @Value(
      "${openbas.expectation.vulnerability.expiration-time:${openaev.expectation.vulnerability.expiration-time:#{null}}}")
  private Long vulnerabilityExpirationTime;

  @Value(
      "${openbas.expectation.human.expiration-time:${openaev.expectation.human.expiration-time:#{null}}}")
  private Long humanExpirationTime;

  @Value(
      "${openbas.expectation.challenge.expiration-time:${openaev.expectation.challenge.expiration-time:#{null}}}")
  private Long challengeExpirationTime;

  @Value(
      "${openbas.expectation.article.expiration-time:${openaev.expectation.article.expiration-time:#{null}}}")
  private Long articleExpirationTime;

  @Value(
      "${openbas.expectation.manual.expiration-time:${openaev.expectation.manual.expiration-time:#{null}}}")
  private Long manualExpirationTime;

  @Value(
      "${openbas.expectation.manual.default-score-value:${openaev.expectation.manual.default-score-value:#{null}}}")
  private Integer defaultManualExpectationScore;

  /**
   * Gets the expiration time for detection expectations.
   *
   * <p>Falls back to technical expiration time, then to the default technical expiration.
   *
   * @return expiration time in seconds
   */
  public long getDetectionExpirationTime() {
    return ofNullable(this.detectionExpirationTime)
        .orElse(
            ofNullable(this.technicalExpirationTime)
                .orElse(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME));
  }

  /**
   * Gets the expiration time for prevention expectations.
   *
   * <p>Falls back to technical expiration time, then to the default technical expiration.
   *
   * @return expiration time in seconds
   */
  public long getPreventionExpirationTime() {
    return ofNullable(this.preventionExpirationTime)
        .orElse(
            ofNullable(this.technicalExpirationTime)
                .orElse(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME));
  }

  /**
   * Gets the expiration time for vulnerability expectations.
   *
   * <p>Falls back to technical expiration time, then to the default technical expiration.
   *
   * @return expiration time in seconds
   */
  public long getVulnerabilityExpirationTime() {
    return ofNullable(this.vulnerabilityExpirationTime)
        .orElse(
            ofNullable(this.technicalExpirationTime)
                .orElse(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME));
  }

  /**
   * Gets the expiration time for challenge expectations.
   *
   * <p>Falls back to human expiration time, then to the default human expiration.
   *
   * @return expiration time in seconds
   */
  public long getChallengeExpirationTime() {
    return ofNullable(this.challengeExpirationTime)
        .orElse(
            ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME));
  }

  /**
   * Gets the expiration time for article expectations.
   *
   * <p>Falls back to human expiration time, then to the default human expiration.
   *
   * @return expiration time in seconds
   */
  public long getArticleExpirationTime() {
    return ofNullable(this.articleExpirationTime)
        .orElse(
            ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME));
  }

  /**
   * Gets the expiration time for manual expectations.
   *
   * <p>Falls back to human expiration time, then to the default human expiration.
   *
   * @return expiration time in seconds
   */
  public long getManualExpirationTime() {
    return ofNullable(this.manualExpirationTime)
        .orElse(
            ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME));
  }

  /**
   * Gets the default score value for manual expectations.
   *
   * <p>Validates that the configured score is within the acceptable range (0-100). If invalid,
   * returns the default score and logs a warning.
   *
   * @return the default score value (0-100)
   */
  public int getDefaultExpectationScoreValue() {
    if (defaultManualExpectationScore == null
        || defaultManualExpectationScore < MIN_SCORE
        || defaultManualExpectationScore > MAX_SCORE) {
      log.warn(
          "Invalid default score value configured: {}. Expected range: {}-{}. Using default: {}",
          defaultManualExpectationScore,
          MIN_SCORE,
          MAX_SCORE,
          DEFAULT_MANUAL_EXPECTATION_SCORE);
      return DEFAULT_MANUAL_EXPECTATION_SCORE;
    }
    return defaultManualExpectationScore;
  }

  /**
   * Gets the expiration time for a specific expectation type.
   *
   * @param type the expectation type
   * @return the expiration time in seconds for the given type
   * @throws NullPointerException if type is null
   */
  public long getExpirationTimeByType(@NotNull final EXPECTATION_TYPE type) {
    return switch (type) {
      case DETECTION -> getDetectionExpirationTime();
      case PREVENTION -> getPreventionExpirationTime();
      case VULNERABILITY -> getVulnerabilityExpirationTime();
      case CHALLENGE -> getChallengeExpirationTime();
      case ARTICLE -> getArticleExpirationTime();
      case MANUAL -> getManualExpirationTime();
      case DOCUMENT, TEXT -> getHumanExpirationTimeOrDefault();
    };
  }

  /**
   * Gets the human expiration time or the default if not configured.
   *
   * @return expiration time in seconds
   */
  private long getHumanExpirationTimeOrDefault() {
    return ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME);
  }
}
