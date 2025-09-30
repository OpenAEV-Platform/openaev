package io.openbas.engine.model.injectexpectation;

import io.openbas.annotation.EsQueryable;
import io.openbas.annotation.Indexable;
import io.openbas.annotation.Queryable;
import io.openbas.database.model.Filters;
import io.openbas.database.model.InjectExpectation.EXPECTATION_STATUS;
import io.openbas.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openbas.engine.model.EsBase;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "expectation-inject", label = "Inject expectation", ref = "InjectExpectation")
public class EsInjectExpectation extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(
      label = "inject expectation type",
      filterable = true,
      refEnumClazz = EXPECTATION_TYPE.class)
  @EsQueryable(keyword = true)
  private String inject_expectation_type;

  @Queryable(label = "inject expectation name")
  private String inject_expectation_name;

  @Queryable(label = "inject title of the inject expectation")
  private String inject_title;

  @Queryable(label = "inject expectation description")
  private String inject_expectation_description;

  @Queryable(label = "inject expectation results")
  private String inject_expectation_results;

  @Queryable(label = "inject expectation score", filterable = true)
  private Double inject_expectation_score;

  @Queryable(label = "inject expectation expected score", filterable = true)
  private Double inject_expectation_expected_score;

  @Queryable(label = "inject expectation expiration time")
  private Long inject_expectation_expiration_time;

  @Queryable(label = "inject expectation is group")
  private Boolean inject_expectation_group;

  @Queryable(
      label = "inject expectation status",
      filterable = true,
      refEnumClazz = EXPECTATION_STATUS.class)
  @EsQueryable(keyword = true)
  private String inject_expectation_status;

  @Queryable(label = "execution date", filterable = true, sortable = true)
  private Instant execution_date;

  // -- SIDE --

  @Queryable(label = "simulation", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_simulation_side; // Must finish by _side

  @Queryable(label = "scenario", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_scenario_side; // Must finish by _side

  @Queryable(label = "inject")
  @EsQueryable(keyword = true)
  private String base_inject_side; // Must finish by _side

  @Queryable(label = "user")
  @EsQueryable(keyword = true)
  private String base_user_side; // Must finish by _side

  @Queryable(label = "team", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_team_side; // Must finish by _side

  @Queryable(
      label = "asset",
      filterable = true,
      overrideOperators = {Filters.FilterOperator.empty, Filters.FilterOperator.not_empty})
  @EsQueryable(keyword = true)
  private String base_asset_side; // Must finish by _side

  @Queryable(
      label = "asset group",
      filterable = true,
      overrideOperators = {Filters.FilterOperator.empty, Filters.FilterOperator.not_empty})
  @EsQueryable(keyword = true)
  private String base_asset_group_side; // Must finish by _side

  @Queryable(
      label = "attack patterns",
      filterable = true,
      dynamicValues = true,
      clazz = String.class)
  @EsQueryable(keyword = true)
  private Set<String> base_attack_patterns_side; // Must finish by _side

  @Queryable(label = "security platform", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_security_platforms_side; // Must finish by _side
}
