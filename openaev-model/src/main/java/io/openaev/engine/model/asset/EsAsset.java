package io.openaev.engine.model.asset;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.engine.model.tenant.EsTenantBase;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Indexed document of the unified asset inventory: every asset the inventory lists (endpoints, AI
 * targets, cloud / web / network / generic assets), matching {@code POST /api/assets/search}.
 * Security platforms are excluded - they are a distinct concept with their own index.
 */
@Getter
@Setter
@Indexable(index = "asset", label = "Asset")
public class EsAsset extends EsTenantBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  // -- ASSET GENERIC --

  @Queryable(label = "asset name", filterable = true)
  @EsQueryable(keyword = true)
  private String asset_name;

  @Queryable(label = "asset description", filterable = true)
  @EsQueryable(keyword = true)
  private String asset_description;

  @Queryable(label = "asset external reference")
  @EsQueryable(keyword = true)
  private String asset_external_reference;

  @Queryable(label = "asset category", filterable = true, refEnumClazz = AssetCategory.class)
  @EsQueryable(keyword = true)
  private String asset_category;

  @Queryable(label = "asset ips", filterable = true)
  @EsQueryable(keyword = true)
  private Set<String> asset_ips;

  @Queryable(label = "asset hostname", filterable = true)
  @EsQueryable(keyword = true)
  private String asset_hostname;

  @Queryable(label = "asset mac addresses", filterable = true)
  @EsQueryable(keyword = true)
  private Set<String> asset_mac_addresses;

  @Queryable(label = "asset seen ip", filterable = true)
  @EsQueryable(keyword = true)
  private String asset_seen_ip;

  // -- ENDPOINT SPECIFIC --
  // Host attributes, so they keep the endpoint_ prefix of their SQL columns (they live on the
  // Endpoint subclass): null on every asset that is not a host, which is exactly how they read in
  // a filter. Filtering hosts only is expressed with asset_category.

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
