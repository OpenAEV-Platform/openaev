package io.openaev.rest.catalog_connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@Schema(description = "Define the ids linked to a collector")
public class ConnectorIds {
  @JsonProperty("connector_instance_id")
  private String connectorInstanceId;

  @JsonProperty("catalog_connector_id")
  private String catalogConnectorId;
}
