package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for the attack observation indexing query. One row per verified
 * {@code (tenant, endpoint, technique, expectation type, scenario)} grain.
 *
 * @see io.openaev.engine.model.snapshotobservation.EsAttackObservation
 */
public interface RawAttackObservationIndexing extends RawTenant {

  String getBase_id();

  Instant getBase_updated_at();

  String getBase_asset_side();

  String getBase_scenario_side();

  String getBase_simulation_side();

  String getAttack_pattern_id();

  Set<String> getSecurity_platform_ids();

  Set<String> getPlatforms_succeeded_ids();

  String getAsset_name();

  String getAsset_hostname();

  String getEndpoint_platform();

  String getTenant_name();

  String getAttack_pattern_external_id();

  String getAttack_pattern_name();

  String getScenario_name();

  String getSimulation_name();

  String getInject_expectation_type();

  String getStatus();

  Long getAttempts_total();

  Long getAttempts_success();

  Double getCoverage_ratio();

  Instant getLast_verified_at();
}
