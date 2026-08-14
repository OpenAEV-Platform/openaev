package io.openaev.service.finding;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingSeverityBucket;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Normalizes the free-text {@link Finding#getSeverity()} column into a stable {@link
 * FindingSeverityBucket}, and provides a per-{@link ContractOutputType} fallback bucket for the
 * (large majority of) finding-compatible output types whose schema never carries a severity value
 * at all.
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

  // -- Fallback-by-finding-type table --
  //
  // Derived from what each output type concretely represents, cross-checked against the real
  // injectors that emit it today (Netexec for the AD-enumeration types, Nmap for network
  // enumeration, AI Red Team for Vulnerability). AWS's own contract emits everything as generic
  // ContractOutputType.Text (no structured schema at all on the injector side), so it always
  // falls into UNKNOWN below rather than being guessed from free text.
  private static final Map<ContractOutputType, FindingSeverityBucket> TYPE_DEFAULT_BUCKET =
      Map.ofEntries(
          // Credential/identity exposure and direct AD privilege-escalation primitives: treated
          // as HIGH by default because they are rarely benign findings.
          Map.entry(ContractOutputType.Credentials, FindingSeverityBucket.HIGH),
          Map.entry(ContractOutputType.AsreproastableAccount, FindingSeverityBucket.HIGH),
          Map.entry(ContractOutputType.KerberoastableAccount, FindingSeverityBucket.HIGH),
          Map.entry(ContractOutputType.AccountWithPasswordNotRequired, FindingSeverityBucket.HIGH),
          Map.entry(ContractOutputType.Delegation, FindingSeverityBucket.HIGH),
          Map.entry(ContractOutputType.Vulnerability, FindingSeverityBucket.HIGH),
          // Reconnaissance-grade findings: real signal, but not yet an exploitable condition by
          // themselves.
          Map.entry(ContractOutputType.Sid, FindingSeverityBucket.MEDIUM),
          Map.entry(ContractOutputType.Share, FindingSeverityBucket.MEDIUM),
          Map.entry(ContractOutputType.File, FindingSeverityBucket.MEDIUM),
          Map.entry(ContractOutputType.AdminUsername, FindingSeverityBucket.MEDIUM),
          // Pure enumeration/configuration visibility, no exploit primitive on their own.
          Map.entry(ContractOutputType.Group, FindingSeverityBucket.LOW),
          Map.entry(ContractOutputType.Computer, FindingSeverityBucket.LOW),
          Map.entry(ContractOutputType.Username, FindingSeverityBucket.LOW),
          Map.entry(ContractOutputType.PasswordPolicy, FindingSeverityBucket.LOW),
          Map.entry(ContractOutputType.PortsScan, FindingSeverityBucket.LOW),
          Map.entry(ContractOutputType.Port, FindingSeverityBucket.LOW),
          Map.entry(ContractOutputType.IPv4, FindingSeverityBucket.LOW),
          Map.entry(ContractOutputType.IPv6, FindingSeverityBucket.LOW),
          // No structured schema on the injector side (e.g. AWS emits everything as raw Text):
          // never guess a severity from free text, be explicit that we don't know.
          Map.entry(ContractOutputType.Text, FindingSeverityBucket.UNKNOWN),
          Map.entry(ContractOutputType.Number, FindingSeverityBucket.UNKNOWN));

  /**
   * Normalizes a finding's severity into a stable bucket.
   *
   * <p>Order of resolution: (1) parse {@code finding.getSeverity()} as a raw CVSS numeric score,
   * (2) match it as a known CVE/OCSF text label, (3) fall back to the per-{@code finding_type}
   * default table above, (4) {@code UNKNOWN} if nothing matched - never silently defaulted to
   * {@code MEDIUM}.
   */
  public FindingSeverityBucket normalize(Finding finding) {
    String rawSeverity = finding.getSeverity();
    if (rawSeverity != null && !rawSeverity.isBlank()) {
      FindingSeverityBucket fromCvss = fromCvssScore(rawSeverity);
      if (fromCvss != null) {
        return fromCvss;
      }
      FindingSeverityBucket fromLabel = fromSeverityLabel(rawSeverity);
      if (fromLabel != null) {
        return fromLabel;
      }
      log.warn(
          "Unrecognized finding_severity value '{}' for finding {} (type={}); falling back to"
              + " UNKNOWN rather than guessing",
          rawSeverity,
          finding.getId(),
          finding.getType());
    }
    return TYPE_DEFAULT_BUCKET.getOrDefault(finding.getType(), FindingSeverityBucket.UNKNOWN);
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
      // CVSS 0.1-3.9 is LOW; 0.0 is technically "None" but we have no bucket for a finding with
      // zero severity that still got created, so it is treated as LOW rather than UNKNOWN.
      return FindingSeverityBucket.LOW;
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
