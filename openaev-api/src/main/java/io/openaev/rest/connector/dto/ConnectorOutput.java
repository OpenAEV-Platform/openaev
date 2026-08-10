package io.openaev.rest.connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
import java.time.Instant;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public abstract class ConnectorOutput {

  @JsonProperty("catalog")
  private CatalogConnectorSimpleOutput catalog;

  @JsonProperty("is_verified")
  private boolean verified = false;

  @JsonProperty("connector_instance")
  private ConnectorInstanceOutput connectorInstance;

  @JsonProperty("is_external")
  private boolean external = false;

  @JsonProperty("can_read")
  boolean canRead;

  // The builtIn auto start are not manageable
  @JsonProperty("can_manage")
  boolean canManage;

  @JsonProperty("last_execution")
  private Instant lastExecution;
}
