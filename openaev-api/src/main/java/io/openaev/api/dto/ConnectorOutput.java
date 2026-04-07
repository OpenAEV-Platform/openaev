package io.openaev.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.openaev.api.connector_instance.dto.ConnectorInstanceOutput;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public abstract class ConnectorOutput {

  @JsonProperty("catalog")
  private CatalogConnectorSimpleOutput catalog;

  @JsonProperty("is_verified")
  private boolean verified = false;

  @JsonProperty("connector_instance")
  private ConnectorInstanceOutput connectorInstance;
}
