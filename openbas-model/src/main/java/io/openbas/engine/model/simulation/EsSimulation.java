package io.openbas.engine.model.simulation;

import io.openbas.annotation.EsQueryable;
import io.openbas.annotation.Indexable;
import io.openbas.annotation.Queryable;
import io.openbas.database.model.Endpoint;
import io.openbas.database.model.ExerciseStatus;
import io.openbas.engine.model.EsBase;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "simulation", label = "Simulation")
public class EsSimulation extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "simulation name", filterable = true)
  private String name;

  @Queryable(label = "simulation status", filterable = true, refEnumClazz = ExerciseStatus.class)
  @EsQueryable(keyword = true)
  private String status;

  @Queryable(label = "execution date", filterable = true, sortable = true)
  private Instant execution_date;

  // -- SIDE --

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

  @Queryable(label = "scenario", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_scenario_side; // Must finish by _side

  // -- SIDE DENORMALIZED --
  // like side but directly names instead of ids in the Set
  // Don't forget to keep track of updated_at values in the SQL query indexing for those attributes
  // denormalized

  @Queryable(label = "platforms", filterable = true, refEnumClazz = Endpoint.PLATFORM_TYPE.class)
  @EsQueryable(keyword = true)
  private Set<String> base_platforms_side_denormalized;
}
