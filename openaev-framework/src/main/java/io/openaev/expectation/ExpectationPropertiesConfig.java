package io.openaev.expectation;

import static java.util.Optional.ofNullable;

import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Setter
@Slf4j
public class ExpectationPropertiesConfig {

  public static long DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME = 21600L; // 6 hours
  public static long DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME = 86400L; // 24 hours
  public static int DEFAULT_MANUAL_EXPECTATION_SCORE = 50;

  @Value("${openaev.expectation.technical.expiration-time:#{null}}")
  private Long technicalExpirationTime;

  @Value("${openaev.expectation.detection.expiration-time:#{null}}")
  private Long detectionExpirationTime;

  @Value("${openaev.expectation.prevention.expiration-time:#{null}}")
  private Long preventionExpirationTime;

  @Value("${openaev.expectation.vulnerability.expiration-time:#{null}}")
  private Long vulnerabilityExpirationTime;

  @Value("${openaev.expectation.human.expiration-time:#{null}}")
  private Long humanExpirationTime;

  @Value("${openaev.expectation.challenge.expiration-time:#{null}}")
  private Long challengeExpirationTime;

  @Value("${openaev.expectation.article.expiration-time:#{null}}")
  private Long articleExpirationTime;

  @Value("${openaev.expectation.manual.expiration-time:#{null}}")
  private Long manualExpirationTime;

  @Value("${openaev.expectation.manual.default-score-value:#{null}}")
  private Integer defaultManualExpectationScore;

  public long getDetectionExpirationTime() {
    return ofNullable(this.detectionExpirationTime)
        .orElse(
            ofNullable(this.technicalExpirationTime)
                .orElse(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME));
  }

  public long getPreventionExpirationTime() {
    return ofNullable(this.preventionExpirationTime)
        .orElse(
            ofNullable(this.technicalExpirationTime)
                .orElse(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME));
  }

  public long getVulnerabilityExpirationTime() {
    return ofNullable(this.vulnerabilityExpirationTime)
        .orElse(
            ofNullable(this.technicalExpirationTime)
                .orElse(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME));
  }

  public long getChallengeExpirationTime() {
    return ofNullable(this.challengeExpirationTime)
        .orElse(
            ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME));
  }

  public long getArticleExpirationTime() {
    return ofNullable(this.articleExpirationTime)
        .orElse(
            ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME));
  }

  public long getManualExpirationTime() {
    return ofNullable(this.manualExpirationTime)
        .orElse(
            ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME));
  }

  public int getDefaultExpectationScoreValue() {
    if (defaultManualExpectationScore == null
        || defaultManualExpectationScore < 1
        || defaultManualExpectationScore > 100) {
      log.warn(
          "The provided default score value is invalid. It should be within the acceptable range of 0 to 100. The score will be set to the default of 50.");
      return DEFAULT_MANUAL_EXPECTATION_SCORE;
    }
    return defaultManualExpectationScore;
  }

  public long getExpirationTimeByType(@NotNull final EXPECTATION_TYPE type) {
    return switch (type) {
      case DETECTION -> getDetectionExpirationTime();
      case PREVENTION -> getPreventionExpirationTime();
      case VULNERABILITY -> getVulnerabilityExpirationTime();
      case CHALLENGE -> getChallengeExpirationTime();
      case ARTICLE -> getArticleExpirationTime();
      case MANUAL -> getManualExpirationTime();
      case DOCUMENT, TEXT ->
          ofNullable(this.humanExpirationTime).orElse(DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME);
    };
  }
}
