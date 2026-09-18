package io.openaev.service.finding;

import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingSeverityBucket;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Normalizes the free-text {@link Finding#getSeverity()} column into a stable {@link
 * FindingSeverityBucket}. Stable findings use the highest reliable signal reported by an occurrence
 * or inherited from an affected asset.
 *
 * <p>Background (see the finding-page risk-score design discussion): only {@code
 * CVEOutputProcessor} and {@code OCSFOutputProcessor} ever populate {@code finding_severity}, and
 * even within CVE the format is inconsistent (raw CVSS numeric strings like {@code "7.5"} next to
 * free-text labels like {@code "high"}). Every other finding-compatible output type (Credentials,
 * Vulnerability, Delegation, PortScan, ...) has no severity field in its {@code
 * ContractOutputField} schema whatsoever - confirmed by inspecting every {@code *OutputProcessor}
 * in {@code io.openaev.output_processor} - so a per-type default is the only available signal for
 * those, and is deliberately conservative rather than a guess: it is never used to override or hide
 * a real severity value when one is present.
 */
@Slf4j
@Service
public class SeverityNormalizationService {

  /**
   * Normalizes a finding's severity into a stable bucket.
   *
   * <p>Credentials are always critical. Other types use their explicit CVSS/label severity and
   * affected asset criticality, taking the highest bucket. Missing signals remain {@code UNKNOWN}.
   */
  public FindingSeverityBucket normalize(Finding finding) {
    return normalize(
        finding.getType(),
        Collections.singletonList(finding.getSeverity()),
        finding.getAssets().stream().map(asset -> asset.getCriticality()).toList());
  }

  /** Resolves the effective severity shared by stable Finding output, filters and facet counts. */
  public FindingSeverityBucket normalize(
      ContractOutputType type,
      Collection<String> observedSeverities,
      Collection<AssetCriticality> assetCriticalities) {
    if (ContractOutputType.Credentials.equals(type)) {
      return FindingSeverityBucket.CRITICAL;
    }
    FindingSeverityBucket observed =
        observedSeverities.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(this::normalizeObservedSeverity)
            .max(Comparator.comparingInt(this::rank))
            .orElse(FindingSeverityBucket.UNKNOWN);
    FindingSeverityBucket fromAssets =
        assetCriticalities.stream()
            .map(this::fromAssetCriticality)
            .max(Comparator.comparingInt(this::rank))
            .orElse(FindingSeverityBucket.UNKNOWN);
    return rank(observed) >= rank(fromAssets) ? observed : fromAssets;
  }

  private FindingSeverityBucket normalizeObservedSeverity(String rawSeverity) {
    FindingSeverityBucket fromCvss = fromCvssScore(rawSeverity);
    if (fromCvss != null) {
      return fromCvss;
    }
    FindingSeverityBucket fromLabel = fromSeverityLabel(rawSeverity);
    if (fromLabel != null) {
      return fromLabel;
    }
    log.warn("Unrecognized finding severity '{}'; using UNKNOWN", rawSeverity);
    return FindingSeverityBucket.UNKNOWN;
  }

  private FindingSeverityBucket fromAssetCriticality(AssetCriticality criticality) {
    if (criticality == null) {
      return FindingSeverityBucket.UNKNOWN;
    }
    return switch (criticality) {
      case VERY_HIGH -> FindingSeverityBucket.CRITICAL;
      case HIGH -> FindingSeverityBucket.HIGH;
      case MEDIUM -> FindingSeverityBucket.MEDIUM;
      case LOW -> FindingSeverityBucket.LOW;
      case UNKNOWN -> FindingSeverityBucket.UNKNOWN;
    };
  }

  private int rank(FindingSeverityBucket bucket) {
    return switch (bucket) {
      case CRITICAL -> 4;
      case HIGH -> 3;
      case MEDIUM -> 2;
      case LOW -> 1;
      case UNKNOWN -> 0;
    };
  }

  /** CVSS v3 base score ranges, per the FIRST.org CVSS v3.1 specification. */
  private FindingSeverityBucket fromCvssScore(String rawSeverity) {
    try {
      double score = Double.parseDouble(rawSeverity.trim());
      if (score < 0.0 || score > 10.0) {
        return null;
      }
      if (score >= 9.0) {
        return FindingSeverityBucket.CRITICAL;
      }
      if (score >= 7.0) {
        return FindingSeverityBucket.HIGH;
      }
      if (score >= 4.0) {
        return FindingSeverityBucket.MEDIUM;
      }
      return score > 0.0 ? FindingSeverityBucket.LOW : FindingSeverityBucket.UNKNOWN;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** CVE free-text labels and OCSF spec label strings, matched case-insensitively. */
  private FindingSeverityBucket fromSeverityLabel(String rawSeverity) {
    String normalized = rawSeverity.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "critical" -> FindingSeverityBucket.CRITICAL;
      case "high" -> FindingSeverityBucket.HIGH;
      case "medium" -> FindingSeverityBucket.MEDIUM;
      case "low" -> FindingSeverityBucket.LOW;
      // OCSF's own "Informational" and "Unknown" severity labels both map to our UNKNOWN bucket:
      // neither carries an actionable severity signal.
      case "informational", "unknown", "other" -> FindingSeverityBucket.UNKNOWN;
      default -> null;
    };
  }
}
