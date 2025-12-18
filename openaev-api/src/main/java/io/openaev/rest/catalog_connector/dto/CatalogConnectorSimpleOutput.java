package io.openaev.rest.catalog_connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Catalog simple output")
public class CatalogConnectorSimpleOutput {
  @JsonProperty("catalog_connector_id")
  private String id;

  @JsonProperty("catalog_connector_short_description")
  private String shortDescription;

  @JsonProperty("catalog_connector_logo_url")
  private String logoUrl;
}
