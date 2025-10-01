package io.openaev.engine.model.finding;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.database.model.ContractOutputType;
import io.openaev.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "finding", label = "Finding")
public class EsFinding extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "finding value", filterable = true)
  @EsQueryable(keyword = true)
  private String finding_value;

  @Queryable(label = "finding type", filterable = true, refEnumClazz = ContractOutputType.class)
  @EsQueryable(keyword = true)
  private String finding_type;

  @Queryable(label = "field")
  private String finding_field;

  // -- SIDE --

  @Queryable(label = "inject", filterable = true)
  @EsQueryable(keyword = true)
  private String base_inject_side; // Must finish by _side

  @Queryable(label = "simulation", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_simulation_side; // Must finish by _side

  @Queryable(label = "scenario", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_scenario_side; // Must finish by _side

  @Queryable(label = "endpoint", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_endpoint_side; // Must finish by _side
}
