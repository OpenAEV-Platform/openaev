package io.openbas.engine.model.inject;

import io.openbas.annotation.EsQueryable;
import io.openbas.annotation.Indexable;
import io.openbas.annotation.Queryable;
import io.openbas.database.model.Endpoint;
import io.openbas.database.model.ExecutionStatus;
import io.openbas.engine.model.EsBase;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "inject", label = "Inject")
public class EsInject extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "inject title")
  private String inject_title;

  @Queryable(label = "inject status", filterable = true, refEnumClazz = ExecutionStatus.class)
  @EsQueryable(keyword = true)
  private String inject_status;

  @Queryable(label = "execution date", filterable = true, sortable = true)
  private Instant execution_date;

  // -- SIDE --

  @Queryable(label = "scenario", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_scenario_side; // Must finish by _side

  @Queryable(label = "simulation", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_simulation_side; // Must finish by _side

  @Queryable(label = "attack patterns", filterable = true)
  @EsQueryable(keyword = true)
  private Set<String> base_attack_patterns_side; // Must finish by _side

  @Queryable(label = "inject children")
  @EsQueryable(keyword = true)
  private Set<String> base_inject_children_side; // Must finish by _side

  @Queryable(label = "attack patterns children")
  @EsQueryable(keyword = true)
  private Set<String> base_attack_patterns_children_side; // Must finish by _side

  @Queryable(label = "kill chain phases")
  @EsQueryable(keyword = true)
  private Set<String> base_kill_chain_phases_side; // Must finish by _side

  @Queryable(label = "inject contract")
  @EsQueryable(keyword = true)
  private String base_inject_contract_side; // Must finish by _side

  @Queryable(label = "tags", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_tags_side; // Must finish by _side

  @Queryable(label = "assets", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_assets_side; // Must finish by _side

  @Queryable(label = "asset groups", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_asset_groups_side; // Must finish by _side

  @Queryable(label = "teams", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_teams_side; // Must finish by _side

  // -- SIDE DENORMALIZED --
  // like side but directly names instead of ids in the Set
  // Don't forget to keep track of updated_at values in the SQL query indexing for those attributes
  // denormalized

  @Queryable(label = "platforms", filterable = true, refEnumClazz = Endpoint.PLATFORM_TYPE.class)
  @EsQueryable(keyword = true)
  private Set<String> base_platforms_side_denormalized;
}
