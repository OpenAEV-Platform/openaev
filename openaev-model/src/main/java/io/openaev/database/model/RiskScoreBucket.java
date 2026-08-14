package io.openaev.database.model;

/**
 * Combined risk-score bucket produced by {@link
 * io.openaev.service.finding.RiskScoreService#computeRiskScore(FindingSeverityBucket,
 * AssetCriticality)} from a {@link FindingSeverityBucket} x {@link AssetCriticality} pair.
 *
 * <p>Not a raw multiplication of the two axes: a high-severity finding never degrades below {@code
 * HIGH} regardless of a low/unknown asset criticality, and {@code NOT_ENOUGH_DATA} is only ever
 * returned for the {@code UNKNOWN x UNKNOWN} corner of the matrix - every other combination
 * resolves to one of the four real buckets, optionally flagged as {@code estimated} by the caller
 * when either input axis was itself a fallback/default value rather than an observed one.
 */
public enum RiskScoreBucket {
  CRITICAL,
  HIGH,
  MEDIUM,
  LOW,
  NOT_ENOUGH_DATA;
}
