package io.openaev.service.finding;

import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.FindingSeverityBucket;
import io.openaev.database.model.RiskScoreBucket;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Combines a {@link FindingSeverityBucket} and an {@link AssetCriticality} into a single {@link
 * RiskScoreBucket}, following the severity x criticality matrix agreed for the Findings page
 * risk-score feature.
 *
 * <p>This is a lookup matrix, not a raw multiplication of the two axes, so that two design rules
 * hold everywhere:
 *
 * <ul>
 *   <li>A {@code CRITICAL} or {@code HIGH} severity finding never degrades below {@code HIGH} risk,
 *       regardless of how low (or unknown) the asset's criticality is - a critical vulnerability is
 *       still worth an analyst's attention even on a low-criticality asset.
 *   <li>{@code NOT_ENOUGH_DATA} is returned only for the single {@code UNKNOWN x UNKNOWN} corner of
 *       the matrix - every other combination, including a single unknown axis, resolves to one of
 *       the four real risk buckets using the best available signal.
 * </ul>
 */
@Service
public class RiskScoreService {

  private record MatrixKey(FindingSeverityBucket severity, AssetCriticality criticality) {}

  private static final Map<MatrixKey, RiskScoreBucket> MATRIX =
      Map.ofEntries(
          // -- CRITICAL severity: always CRITICAL or HIGH, never lower --
          entry(
              FindingSeverityBucket.CRITICAL, AssetCriticality.VERY_HIGH, RiskScoreBucket.CRITICAL),
          entry(FindingSeverityBucket.CRITICAL, AssetCriticality.HIGH, RiskScoreBucket.CRITICAL),
          entry(FindingSeverityBucket.CRITICAL, AssetCriticality.MEDIUM, RiskScoreBucket.CRITICAL),
          entry(FindingSeverityBucket.CRITICAL, AssetCriticality.LOW, RiskScoreBucket.HIGH),
          entry(FindingSeverityBucket.CRITICAL, AssetCriticality.UNKNOWN, RiskScoreBucket.HIGH),

          // -- HIGH severity: HIGH, degrading to MEDIUM only on a confirmed low-criticality asset
          entry(FindingSeverityBucket.HIGH, AssetCriticality.VERY_HIGH, RiskScoreBucket.CRITICAL),
          entry(FindingSeverityBucket.HIGH, AssetCriticality.HIGH, RiskScoreBucket.HIGH),
          entry(FindingSeverityBucket.HIGH, AssetCriticality.MEDIUM, RiskScoreBucket.HIGH),
          entry(FindingSeverityBucket.HIGH, AssetCriticality.LOW, RiskScoreBucket.MEDIUM),
          entry(FindingSeverityBucket.HIGH, AssetCriticality.UNKNOWN, RiskScoreBucket.HIGH),

          // -- MEDIUM severity: scales with asset criticality --
          entry(FindingSeverityBucket.MEDIUM, AssetCriticality.VERY_HIGH, RiskScoreBucket.HIGH),
          entry(FindingSeverityBucket.MEDIUM, AssetCriticality.HIGH, RiskScoreBucket.MEDIUM),
          entry(FindingSeverityBucket.MEDIUM, AssetCriticality.MEDIUM, RiskScoreBucket.MEDIUM),
          entry(FindingSeverityBucket.MEDIUM, AssetCriticality.LOW, RiskScoreBucket.LOW),
          entry(FindingSeverityBucket.MEDIUM, AssetCriticality.UNKNOWN, RiskScoreBucket.MEDIUM),

          // -- LOW severity: scales with asset criticality, but a VERY_HIGH-criticality asset
          // still bumps it into MEDIUM so it isn't lost among noise on the most important assets
          entry(FindingSeverityBucket.LOW, AssetCriticality.VERY_HIGH, RiskScoreBucket.MEDIUM),
          entry(FindingSeverityBucket.LOW, AssetCriticality.HIGH, RiskScoreBucket.LOW),
          entry(FindingSeverityBucket.LOW, AssetCriticality.MEDIUM, RiskScoreBucket.LOW),
          entry(FindingSeverityBucket.LOW, AssetCriticality.LOW, RiskScoreBucket.LOW),
          entry(FindingSeverityBucket.LOW, AssetCriticality.UNKNOWN, RiskScoreBucket.LOW),

          // -- UNKNOWN severity: rely entirely on asset criticality, since that's the only real
          // signal left; only UNKNOWN x UNKNOWN is truly "not enough data"
          entry(FindingSeverityBucket.UNKNOWN, AssetCriticality.VERY_HIGH, RiskScoreBucket.MEDIUM),
          entry(FindingSeverityBucket.UNKNOWN, AssetCriticality.HIGH, RiskScoreBucket.MEDIUM),
          entry(FindingSeverityBucket.UNKNOWN, AssetCriticality.MEDIUM, RiskScoreBucket.LOW),
          entry(FindingSeverityBucket.UNKNOWN, AssetCriticality.LOW, RiskScoreBucket.LOW),
          entry(
              FindingSeverityBucket.UNKNOWN,
              AssetCriticality.UNKNOWN,
              RiskScoreBucket.NOT_ENOUGH_DATA));

  private static Map.Entry<MatrixKey, RiskScoreBucket> entry(
      FindingSeverityBucket severity, AssetCriticality criticality, RiskScoreBucket bucket) {
    return Map.entry(new MatrixKey(severity, criticality), bucket);
  }

  /**
   * Looks up the combined risk-score bucket for a severity/criticality pair.
   *
   * @param severity the finding's normalized severity bucket (see {@link
   *     SeverityNormalizationService#normalize})
   * @param criticality the finding's asset's {@link AssetCriticality}; pass {@code
   *     AssetCriticality.UNKNOWN} when the finding has no linked asset or the asset has no
   *     criticality set
   */
  public RiskScoreBucket computeRiskScore(
      FindingSeverityBucket severity, AssetCriticality criticality) {
    return MATRIX.get(new MatrixKey(severity, criticality));
  }

  /**
   * Whether either input axis was itself a fallback/default value rather than an observed one -
   * callers use this to show an "estimated" badge next to the risk score in the UI rather than
   * presenting it with the same confidence as a fully-observed score.
   */
  public boolean isEstimated(FindingSeverityBucket severity, AssetCriticality criticality) {
    return severity == FindingSeverityBucket.UNKNOWN || criticality == AssetCriticality.UNKNOWN;
  }
}
