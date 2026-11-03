package io.openaev.service.stix;

import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoverageResult {
  private String name;

  private int score;

  public CoverageResult(String name, int successRate) {
    this.name = name;
    this.score = successRate;
  }

  /**
   * Builds the coverage entry of one expectation type.
   *
   * @param result the aggregated results of one expectation type
   * @return the entry named after the expectation type, scored in percentage points
   */
  public static CoverageResult of(ExpectationResultsByType result) {
    return new CoverageResult(result.type().name(), toPercentagePoints(result));
  }

  /**
   * Converts the success rate of aggregated results into the percentage points sent to OpenCTI.
   * Every score of the Security Coverage result bundle goes through this rounding.
   *
   * @param result the aggregated results of one expectation type
   * @return the success rate rounded to the nearest percentage point, from 0 to 100
   */
  public static int toPercentagePoints(ExpectationResultsByType result) {
    return (int) Math.round(result.getSuccessRate() * 100);
  }
}
