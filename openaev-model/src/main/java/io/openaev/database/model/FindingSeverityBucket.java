package io.openaev.database.model;

/**
 * Normalized severity bucket for a {@link Finding}, derived from the free-text {@code
 * finding_severity} column by {@link io.openaev.service.finding.SeverityNormalizationService}.
 *
 * <p>{@code finding_severity} is populated inconsistently across injectors: only the CVE (raw CVSS
 * numeric string or free-text label) and OCSF (spec label string) output processors ever set it,
 * and every other finding-compatible output type (Credentials, Vulnerability, PortScan, ...) never
 * carries a severity value at all - see {@link
 * io.openaev.service.finding.SeverityNormalizationService} for the fallback-by-{@link
 * ContractOutputType} table used when no explicit severity is present.
 *
 * <p>{@code UNKNOWN} is a first-class bucket, never silently collapsed into {@code MEDIUM}: it
 * means "we have no reliable signal", which is a materially different situation from an actually
 * observed medium-severity finding.
 */
public enum FindingSeverityBucket {
  CRITICAL,
  HIGH,
  MEDIUM,
  LOW,
  UNKNOWN;
}
