package io.openaev.rest.attack_pattern.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Tenant-wide MITRE ATT&CK coverage for a single attack pattern, aggregated across simulations.
 *
 * <p>Numbers are computed from the Elasticsearch {@code expectation-inject} documents (the same
 * aggregation that powers the home {@code security-coverage} matrix), grouped by attack pattern.
 * {@code *_success} counts SUCCESS expectation docs and {@code *_total} counts SUCCESS + FAILED
 * docs, independently for the PREVENTION and DETECTION expectation types.
 */
public record AttackPatternCoverageOutput(
    @JsonProperty("attack_pattern_id") String attackPatternId,
    @JsonProperty("attack_pattern_external_id") String attackPatternExternalId,
    @JsonProperty("attack_pattern_name") String attackPatternName,
    @JsonProperty("kill_chain_phases") List<KillChainPhaseCoverage> killChainPhases,
    @JsonProperty("prevention_success") int preventionSuccess,
    @JsonProperty("prevention_total") int preventionTotal,
    @JsonProperty("detection_success") int detectionSuccess,
    @JsonProperty("detection_total") int detectionTotal) {

  /** Minimal kill chain phase projection attached to an attack pattern coverage entry. */
  public record KillChainPhaseCoverage(
      @JsonProperty("phase_id") String phaseId,
      @JsonProperty("phase_name") String phaseName,
      @JsonProperty("phase_external_id") String phaseExternalId,
      @JsonProperty("phase_order") Long phaseOrder) {}
}
