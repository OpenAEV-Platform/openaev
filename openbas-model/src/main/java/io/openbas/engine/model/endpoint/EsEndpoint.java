package io.openbas.engine.model.endpoint;

import io.openbas.annotation.EsQueryable;
import io.openbas.annotation.Indexable;
import io.openbas.annotation.Queryable;
import io.openbas.database.model.Endpoint;
import io.openbas.database.model.Filters;
import io.openbas.engine.model.EsBase;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "endpoint", label = "Endpoint")
public class EsEndpoint extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  // -- ASSET GENERIC
  @Queryable(label = "endpoint name", filterable = true)
  @EsQueryable(keyword = true)
  private String endpoint_name;

  @Queryable(label = "endpoint description", filterable = true)
  @EsQueryable(keyword = true)
  private String endpoint_description;

  @Queryable(label = "endpoint external reference")
  @EsQueryable(keyword = true)
  private String endpoint_external_reference;

  // -- ENDPOINT SPECIFIC --

  @Queryable(label = "endpoint ips", filterable = true)
  @EsQueryable(keyword = true)
  private Set<String> endpoint_ips;

  @Queryable(label = "endpoint hostname", filterable = true)
  @EsQueryable(keyword = true)
  private String endpoint_hostname;

  @Queryable(
      label = "endpoint platform",
      filterable = true,
      dynamicValues = true,
      refEnumClazz = Endpoint.PLATFORM_TYPE.class)
  @EsQueryable(keyword = true)
  private String endpoint_platform;

  @Queryable(
      label = "endpoint arch",
      filterable = true,
      dynamicValues = true,
      refEnumClazz = Endpoint.PLATFORM_ARCH.class)
  @EsQueryable(keyword = true)
  private String endpoint_arch;

  @Queryable(label = "endpoint mac addresses", filterable = true)
  @EsQueryable(keyword = true)
  private Set<String> endpoint_mac_addresses;

  @Queryable(label = "endpoint seen ip", filterable = true)
  @EsQueryable(keyword = true)
  private String endpoint_seen_ip;

  @Queryable(label = "endpoint is end of life", filterable = true)
  @EsQueryable(keyword = true)
  private Boolean endpoint_is_eol;

  // -- SIDE --

  @Queryable(label = "findings", filterable = true)
  @EsQueryable(keyword = true)
  private Set<String> base_findings_side; // Must finish by _side

  @Queryable(label = "tags", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_tags_side; // Must finish by _side

  @Queryable(
      label = "simulation",
      filterable = true,
      dynamicValues = true,
      overrideOperators = {
        Filters.FilterOperator.eq,
        Filters.FilterOperator.not_eq,
        Filters.FilterOperator.empty,
        Filters.FilterOperator.not_empty
      })
  @EsQueryable(keyword = true)
  private Set<String>
      base_simulation_side; // Must finish by _side, no plural (Set) to work as the other generic

  @Queryable(
      label = "scenario",
      filterable = true,
      dynamicValues = true,
      overrideOperators = {
        Filters.FilterOperator.eq,
        Filters.FilterOperator.not_eq,
        Filters.FilterOperator.empty,
        Filters.FilterOperator.not_empty
      })
  @EsQueryable(keyword = true)
  private Set<String>
      base_scenario_side; // Must finish by _side, no plural (Set) to work as the other generic
}
