package io.openaev.api.snapshot;

import io.openaev.api.snapshot.form.AttackObservationOutput;
import io.openaev.api.snapshot.form.VulnerabilityObservationOutput;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import io.openaev.engine.model.snapshotobservation.EsVulnerabilityObservation;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Maps engine documents of the bulk snapshot export to their output DTOs. */
@Component
public class SnapshotObservationMapper {

  public AttackObservationOutput toOutput(EsAttackObservation es) {
    return new AttackObservationOutput(
        es.getBase_id(),
        es.getBase_updated_at(),
        es.getBase_asset_side(),
        es.getBase_scenario_side(),
        es.getBase_simulation_side(),
        es.getBase_security_platforms_side(),
        es.getAsset_name(),
        es.getAsset_hostname(),
        es.getEndpoint_platform(),
        es.getAttack_observation_tenant_name(),
        es.getAttack_observation_attack_pattern_external_id(),
        es.getAttack_observation_attack_pattern_name(),
        es.getAttack_observation_scenario_name(),
        es.getAttack_observation_simulation_name(),
        es.getAttack_observation_expectation_type(),
        es.getAttack_observation_status(),
        es.getAttack_observation_attempts_total(),
        es.getAttack_observation_attempts_success(),
        es.getAttack_observation_coverage_ratio(),
        es.getAttack_observation_platforms_succeeded(),
        es.getAttack_observation_last_verified_at());
  }

  public VulnerabilityObservationOutput toOutput(EsVulnerabilityObservation es) {
    return new VulnerabilityObservationOutput(
        es.getBase_id(),
        es.getBase_updated_at(),
        es.getBase_asset_side(),
        singleElement(es.getBase_findings_side()),
        es.getBase_scenario_side(),
        es.getBase_simulation_side(),
        es.getFinding_type(),
        es.getFinding_value(),
        es.getAsset_name(),
        es.getAsset_hostname(),
        es.getEndpoint_platform(),
        es.getVulnerability_observation_tenant_name(),
        es.getVulnerability_observation_external_id(),
        es.getVulnerability_observation_scenario_name(),
        es.getVulnerability_observation_simulation_name(),
        es.getVulnerability_observation_last_verified_at());
  }

  /**
   * {@code base_findings_side} holds exactly one element by construction (FR5); a set has no order,
   * so this must never be extended to more than one element without revisiting the caller.
   */
  private static String singleElement(Set<String> findings) {
    return findings == null || findings.isEmpty() ? null : findings.iterator().next();
  }
}
