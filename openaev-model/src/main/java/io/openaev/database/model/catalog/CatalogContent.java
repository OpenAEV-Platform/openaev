package io.openaev.database.model.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.Tenant;

/** Content a tenant installed from the catalogue: which repository, ID and version it came from. */
public interface CatalogContent {

  @JsonIgnore
  ContentType getCatalogType();

  @JsonIgnore
  String getCatalogRepository();

  @JsonIgnore
  String getCatalogExternalId();

  @JsonIgnore
  String getCatalogVersion();

  /** The id all its versions share in the tenant. */
  @JsonIgnore
  String getCatalogContentId();

  Tenant getTenant();
}
