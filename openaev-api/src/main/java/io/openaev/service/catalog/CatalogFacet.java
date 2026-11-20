package io.openaev.service.catalog;

import io.openaev.database.model.Endpoint;
import java.util.List;
import java.util.stream.Stream;

/**
 * A facet of an Available tab: the filter key it counts, and the values to count one by one when
 * the key holds a list; without values, the values are read from the items.
 */
public record CatalogFacet(String key, List<String> values) {

  public static final String PLATFORMS_KEY = "catalog_item_platforms";
  public static final String DOMAIN_KEY = "catalog_item_domain";
  public static final String SCENARIO_TYPE_KEY = "catalog_item_scenario_type";
  public static final String CATEGORY_KEY = "catalog_item_category";
  public static final String AUTHOR_KEY = "catalog_item_author";

  public static final CatalogFacet PLATFORMS =
      new CatalogFacet(
          PLATFORMS_KEY,
          Stream.of(
                  Endpoint.PLATFORM_TYPE.Windows,
                  Endpoint.PLATFORM_TYPE.Linux,
                  Endpoint.PLATFORM_TYPE.MacOS)
              .map(Enum::name)
              .toList());

  public static CatalogFacet of(String key) {
    return new CatalogFacet(key, List.of());
  }
}
