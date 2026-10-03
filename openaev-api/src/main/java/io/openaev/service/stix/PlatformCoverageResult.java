package io.openaev.service.stix;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;

/**
 * One entry of the {@code coverage_platforms} property of a {@code has-covered} relationship: the
 * score of one expectation type, computed only from the results of one security platform.
 *
 * <p>OpenCTI stores these entries as {@code coverage_platforms_information}, which is how the
 * validation layer of a covered object is attributed to the platform that detected or prevented it.
 *
 * @param platformRef STIX id of the security platform identity, always present in the same bundle
 * @param name the expectation type, named like {@link CoverageResult#getName()}
 * @param score the success rate in percentage points, rounded like {@link
 *     CoverageResult#getScore()}
 */
public record PlatformCoverageResult(
    @JsonProperty("platform_ref") String platformRef,
    @JsonProperty("name") String name,
    @JsonProperty("score") int score) {

  /**
   * Builds the entry of one expectation type for one security platform.
   *
   * @param platformRef STIX id of the security platform identity
   * @param result the platform's aggregated results of one expectation type
   * @return the entry, scored with the same rounding as the overall coverage
   */
  public static PlatformCoverageResult of(Identifier platformRef, ExpectationResultsByType result) {
    return new PlatformCoverageResult(
        platformRef.getValue(), result.type().name(), CoverageResult.toPercentagePoints(result));
  }
}
