package io.openbas.expectation;

import static java.util.Optional.ofNullable;

import io.openbas.database.model.InjectExpectation.EXPECTATION_TYPE;
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

  @Value("${openbas.expectation.technical.expiration-time:#{null}}")
  private Long technicalExpirationTime;

  @Value("${openbas.expectation.detection.expiration-time:#{null}}")
  private Long detectionExpirationTime;

  @Value("${openbas.expectation.prevention.expiration-time:#{null}}")
  private Long preventionExpirationTime;

  @Value("${openbas.expectation.vulnerability.expiration-time:#{null}}")
  private Long vulnerabilityExpirationTime;

  @Value("${openbas.expectation.human.expiration-time:#{null}}")
  private Long humanExpirationTime;

  @Value("${openbas.expectation.challenge.expiration-time:#{null}}")
  private Long challengeExpirationTime;

  @Value("${openbas.expectation.article.expiration-time:#{null}}")
  private Long articleExpirationTime;

  @Value("${openbas.expectation.manual.expiration-time:#{null}}")
  private Long manualExpirationTime;

  @Value("${openbas.expectation.manual.default-score-value:#{null}}")
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
