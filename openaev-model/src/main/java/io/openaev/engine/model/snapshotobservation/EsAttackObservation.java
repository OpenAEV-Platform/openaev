package io.openaev.engine.model.snapshotobservation;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Endpoint;
import io.openaev.engine.model.tenant.EsTenantBase;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(
    index = "snapshot-attack-observation",
    label = "Attack observation",
    ref = "AttackObservation")
public class EsAttackObservation extends EsTenantBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "asset name", filterable = true)
  @EsQueryable(keyword = true)
  private String asset_name;

  @Queryable(label = "asset hostname", filterable = true)
  @EsQueryable(keyword = true)
  private String asset_hostname;

  @Queryable(
      label = "endpoint platform",
      filterable = true,
      refEnumClazz = Endpoint.PLATFORM_TYPE.class)
  @EsQueryable(keyword = true)
  private String endpoint_platform;

  @Queryable(label = "tenant name", filterable = true)
  @EsQueryable(keyword = true)
  private String attack_observation_tenant_name;

  @Queryable(label = "attack pattern external id", filterable = true)
  @EsQueryable(keyword = true)
  private String attack_observation_attack_pattern_external_id;

  @Queryable(label = "attack pattern name", filterable = true)
  @EsQueryable(keyword = true)
  private String attack_observation_attack_pattern_name;

  @Queryable(label = "scenario name", filterable = true)
  @EsQueryable(keyword = true)
  private String attack_observation_scenario_name;

  @Queryable(label = "simulation name", filterable = true)
  @EsQueryable(keyword = true)
  private String attack_observation_simulation_name;

  @Queryable(
      label = "attack observation expectation type",
      filterable = true,
      refEnumClazz = EXPECTATION_TYPE.class)
  @EsQueryable(keyword = true)
  private String attack_observation_expectation_type;

  @Queryable(
      label = "attack observation status",
      filterable = true,
      refEnumClazz = EXPECTATION_STATUS.class)
  @EsQueryable(keyword = true)
  private String attack_observation_status;

  @Queryable(label = "attack observation attempts total", filterable = true, sortable = true)
  private Long attack_observation_attempts_total;

  @Queryable(label = "attack observation attempts success", filterable = true, sortable = true)
  private Long attack_observation_attempts_success;

  @Queryable(label = "attack observation coverage ratio", filterable = true, sortable = true)
  private Double attack_observation_coverage_ratio;

  @Queryable(label = "attack observation platforms succeeded", filterable = true)
  @EsQueryable(keyword = true)
  private Set<String> attack_observation_platforms_succeeded;

  @Queryable(label = "attack observation last verified at", filterable = true, sortable = true)
  private Instant attack_observation_last_verified_at;

  // -- SIDE --

  @Queryable(label = "asset", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_asset_side; // Must finish by _side

  @Queryable(label = "scenario", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_scenario_side; // Must finish by _side

  @Queryable(label = "simulation", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_simulation_side; // Must finish by _side

  @Queryable(
      label = "attack patterns",
      filterable = true,
      dynamicValues = true,
      clazz = String.class)
  @EsQueryable(keyword = true)
  private Set<String> base_attack_patterns_side; // Must finish by _side

  @Queryable(
      label = "security platforms",
      filterable = true,
      dynamicValues = true,
      clazz = String.class)
  @EsQueryable(keyword = true)
  private Set<String> base_security_platforms_side; // Must finish by _side
}
