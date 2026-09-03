package io.openaev.engine.model.snapshotobservation;

import static io.openaev.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;

import io.openaev.database.raw.RawAttackObservationIndexing;
import io.openaev.database.repository.AttackObservationRepository;
import io.openaev.engine.BulkSnapshotExportCondition;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Conditional(BulkSnapshotExportCondition.class)
public class AttackObservationHandler implements Handler<EsAttackObservation> {

  private final AttackObservationRepository attackObservationRepository;

  @Override
  public List<EsAttackObservation> fetch(Instant from, int limit) {
    return fetch(from, null, limit);
  }

  @Override
  public List<EsAttackObservation> fetch(Instant from, String fromId, int limit) {
    Instant queryFrom = from != null ? from : Instant.EPOCH;
    List<RawAttackObservationIndexing> forIndexing =
        this.attackObservationRepository.findForIndexing(queryFrom, fromId, limit);
    return forIndexing.stream().map(this::toEsAttackObservation).toList();
  }

  @Override
  public boolean isKeysetPaged() {
    return true;
  }

  private EsAttackObservation toEsAttackObservation(RawAttackObservationIndexing raw) {
    EsAttackObservation es = new EsAttackObservation();
    // Base
    es.setBase_id(raw.getBase_id());
    // Representative: the technique observed on the endpoint, e.g. "T1055 on WIN-HOST".
    String target = raw.getAsset_hostname() != null ? raw.getAsset_hostname() : raw.getAsset_name();
    es.setBase_representative(raw.getAttack_pattern_external_id() + " on " + target);
    es.setBase_updated_at(raw.getBase_updated_at());
    es.setBase_tenant_side(raw.getTenant_id());
    // ACL matches base_restrictions against Grant.grant_resource ids, which only ever cover
    // SCENARIO / SIMULATION / ATOMIC_TESTING: an asset id here would hide the document from every
    // non-admin instead of restricting it.
    es.setBase_restrictions(
        buildRestrictions(raw.getBase_scenario_side(), raw.getBase_simulation_side()));
    // The simulation is deliberately not a dependency: deleting one replay must not destroy an
    // observation whose key still holds through the earlier ones.
    es.setBase_dependencies(List.of(raw.getBase_asset_side(), raw.getBase_scenario_side()));

    // Side
    es.setBase_asset_side(raw.getBase_asset_side());
    es.setBase_scenario_side(raw.getBase_scenario_side());
    es.setBase_simulation_side(raw.getBase_simulation_side());
    es.setBase_attack_patterns_side(Set.of(raw.getAttack_pattern_id()));
    es.setBase_security_platforms_side(
        isEmpty(raw.getSecurity_platform_ids())
            ? Set.of()
            : Set.copyOf(raw.getSecurity_platform_ids()));

    // Denormalisation
    es.setAsset_name(raw.getAsset_name());
    es.setAsset_hostname(raw.getAsset_hostname());
    es.setEndpoint_platform(raw.getEndpoint_platform());
    es.setAttack_observation_tenant_name(raw.getTenant_name());
    es.setAttack_observation_attack_pattern_external_id(raw.getAttack_pattern_external_id());
    es.setAttack_observation_attack_pattern_name(raw.getAttack_pattern_name());
    es.setAttack_observation_scenario_name(raw.getScenario_name());
    es.setAttack_observation_simulation_name(raw.getSimulation_name());
    es.setAttack_observation_expectation_type(raw.getInject_expectation_type());
    es.setAttack_observation_status(raw.getStatus());
    es.setAttack_observation_attempts_total(raw.getAttempts_total());
    es.setAttack_observation_attempts_success(raw.getAttempts_success());
    es.setAttack_observation_coverage_ratio(raw.getCoverage_ratio());
    es.setAttack_observation_platforms_succeeded(
        isEmpty(raw.getPlatforms_succeeded_ids())
            ? Set.of()
            : Set.copyOf(raw.getPlatforms_succeeded_ids()));
    es.setAttack_observation_last_verified_at(raw.getLast_verified_at());

    return es;
  }
}
