package io.openaev.utils.fixtures;

import static io.openaev.service.InjectExpectationService.COLLECTOR;

import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.utils.inject_expectation_result.ExpectationResultBuilder;
import java.time.Instant;
import java.util.UUID;

/**
 * Expectation results shaped like the ones the platform writers persist, so attribution tests run
 * on production-like data.
 */
public class InjectExpectationResultFixture {

  public static final String RESULT_LABEL = "Result from unit tests";

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
        .date(Instant.now().toString())
        .score(score)
        .build();
  }

  /** A result written directly by a security platform (assessment injectors). */
  public static InjectExpectationResult createSecurityPlatformResult(
      SecurityPlatform securityPlatform, Double score) {
    return ExpectationResultBuilder.buildForSecurityPlatform(
        securityPlatform, score == null ? null : RESULT_LABEL, score);
  }

  /** A result whose source is not a security platform (manual validation from the UI). */
  public static InjectExpectationResult createManualResult(Double score) {
    return InjectExpectationResult.builder()
        .sourceId("ui")
        .sourceType("manual")
        .sourceName("Manual validation")
        .sourceAssetId("ui")
        .result(RESULT_LABEL)
        .date(Instant.now().toString())
        .score(score)
        .build();
  }
}
