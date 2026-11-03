package io.openaev.utils.fixtures;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static io.openaev.service.InjectExpectationService.COLLECTOR;
import static io.openaev.service.InjectExpectationService.SECURITY_PLATFORM;

import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Expectation results shaped like the ones the platform writers persist, so attribution tests run
 * on production-like data.
 */
public class InjectExpectationResultFixture {

  public static final String RESULT_LABEL = "Result from unit tests";

  public static InjectExpectationResult getExpiredResult() {
    return InjectExpectationResult.builder()
        .date(Instant.now())
        .result(ExpectationsExpirationManagerService.EXPIRED)
        .sourceId(COLLECTOR_ID)
        .sourceName("Expiration Manager")
        .sourceType("expiration-type")
        .sourceAssetId(null)
        .sourcePlatform(null)
        .score(0.0)
        .metadata(null)
        .build();
  }

  /**
   * A result written by a collector linked to a security platform: the source is the collector, the
   * platform is only the source asset ({@code ExpectationResultBuilder.addResult} with a
   * collector).
   */
  public static InjectExpectationResult createCollectorResult(
      SecurityPlatform securityPlatform, Double score) {
    return InjectExpectationResult.builder()
        .sourceId(UUID.randomUUID().toString())
        .sourceType(COLLECTOR)
        .sourceName("Collector of " + securityPlatform.getName())
        .sourcePlatform(securityPlatform.getSecurityPlatformType().name())
        .sourceAssetId(securityPlatform.getId())
        .result(score == null ? null : RESULT_LABEL)
        .date(Instant.now())
        .score(score)
        .build();
  }

  /**
   * A result written directly by a security platform (assessment injectors), shaped like {@code
   * ExpectationResultBuilder.addResult} with a security platform.
   */
  public static InjectExpectationResult createSecurityPlatformResult(
      SecurityPlatform securityPlatform, Double score) {
    return InjectExpectationResult.builder()
        .sourceId(securityPlatform.getId())
        .sourceType(SECURITY_PLATFORM)
        .sourceName(securityPlatform.getName())
        .sourcePlatform(
            securityPlatform.getSecurityPlatformType() != null
                ? securityPlatform.getSecurityPlatformType().name()
                : null)
        .sourceAssetId(securityPlatform.getId())
        .result(score == null ? null : RESULT_LABEL)
        .date(Instant.now())
        .score(score)
        .build();
  }

  /** A result whose source is not a security platform (manual validation from the UI). */
  public static InjectExpectationResult createManualResult(Double score) {
    return InjectExpectationResult.builder()
        .sourceId("ui")
        .sourceType("manual")
        .sourceName("Manual validation")
        .sourceAssetId("ui")
        .result(RESULT_LABEL)
        .date(Instant.now())
        .score(score)
        .build();
  }
}
