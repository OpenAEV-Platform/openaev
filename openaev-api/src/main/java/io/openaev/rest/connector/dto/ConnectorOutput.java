package io.openaev.rest.connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
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
