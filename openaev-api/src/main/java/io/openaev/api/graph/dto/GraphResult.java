package io.openaev.api.graph.dto;

/**
 * The outcome an edge or a node carries from the expectations behind it: the worst wins, so one
 * failed expectation marks the inject-asset link FAILED whatever the others say.
 */
public enum GraphResult {
  SUCCESS,
  PENDING,
  FAILED;

  /**
   * One expectation's result: no score yet is PENDING, a score at or above the expected score is
   * SUCCESS, anything lower is FAILED. A missing expected score counts as fully expected (100).
   */
  public static GraphResult ofScore(Double score, Double expectedScore) {
    if (score == null) {
      return PENDING;
    }
    double expected = expectedScore == null ? 100.0 : expectedScore;
    return score >= expected ? SUCCESS : FAILED;
  }

  /** The worse of two results, null-safe. */
  public static GraphResult worst(GraphResult a, GraphResult b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.ordinal() >= b.ordinal() ? a : b;
  }
}
